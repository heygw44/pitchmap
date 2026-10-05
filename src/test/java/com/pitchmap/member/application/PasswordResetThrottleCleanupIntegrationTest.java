package com.pitchmap.member.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.domain.PasswordResetRequestPolicy;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class PasswordResetThrottleCleanupIntegrationTest {

    private static final Duration ONE_SECOND = Duration.ofSeconds(1);
    // DATETIME 컬럼에 UTC 시각을 문자열로 넣는다. 드라이버의 시간대 변환이 끼어들지 않게 하려는 것이다.
    private static final DateTimeFormatter DATETIME_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC);

    private final AtomicInteger keySequence = new AtomicInteger();

    @Autowired
    private PasswordResetThrottleCleanupService cleanupService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName("[F-02][PW-05] 마지막 요청이 24시간보다 1초 더 오래된 행은 지운다")
    void deletesRowOlderThanStaleAfter() {
        String stale = insertRow(cutoff().minus(ONE_SECOND));

        int deleted = cleanupService.deleteStaleThrottles();

        assertThat(deleted).isEqualTo(1);
        assertThat(exists(stale)).isFalse();
    }

    @Test
    @DisplayName("[F-02][PW-05] 마지막 요청이 정확히 24시간 전인 행은 남기고, 24시간이 되기 1초 전인 행도 남긴다")
    void keepsRowNotOlderThanStaleAfter() {
        String exactly = insertRow(cutoff());
        String justUnder = insertRow(cutoff().plus(ONE_SECOND));

        int deleted = cleanupService.deleteStaleThrottles();

        assertThat(deleted).isZero();
        assertThat(exists(exactly)).isTrue();
        assertThat(exists(justUnder)).isTrue();
    }

    @Test
    @DisplayName("[F-02][PW-05] 최근 행은 남기고 오래된 행만 지운다")
    void keepsRecentRowAndDeletesOnlyStaleRows() {
        String stale = insertRow(cutoff().minus(Duration.ofDays(3)));
        String recent = insertRow(clock.instant());

        int deleted = cleanupService.deleteStaleThrottles();

        assertThat(deleted).isEqualTo(1);
        assertThat(exists(stale)).isFalse();
        assertThat(exists(recent)).isTrue();
    }

    @Test
    @DisplayName("[F-02][PW-05] 시계가 움직여 24시간을 넘기면 그때부터 삭제 대상이 된다")
    void rowBecomesDeletableWhenClockPassesStaleAfter() {
        String key = insertRow(clock.instant());

        assertThat(cleanupService.deleteStaleThrottles()).isZero();
        clock.advance(PasswordResetRequestPolicy.STALE_AFTER);
        assertThat(cleanupService.deleteStaleThrottles()).isZero();
        clock.advance(ONE_SECOND);
        int deleted = cleanupService.deleteStaleThrottles();

        assertThat(deleted).isEqualTo(1);
        assertThat(exists(key)).isFalse();
    }

    @Test
    @DisplayName("[F-02][PW-05] 한 번에 지우는 상한보다 대상이 많아도 모두 삭제하고 지운 수를 돌려준다")
    void deletesAllStaleRowsBeyondBatchSize() {
        int total = PasswordResetThrottleCleanupService.BATCH_SIZE * 2 + 1;
        insertStaleRows(total);
        String recent = insertRow(clock.instant());

        int deleted = cleanupService.deleteStaleThrottles();

        assertThat(deleted).isEqualTo(total);
        assertThat(count()).isEqualTo(1);
        assertThat(exists(recent)).isTrue();
    }

    @Test
    @DisplayName("[F-02][PW-05] 대상이 정확히 상한과 같아도 모두 지우고 지운 수를 돌려준다")
    void deletesAllStaleRowsWhenCountEqualsBatchSize() {
        int total = PasswordResetThrottleCleanupService.BATCH_SIZE;
        insertStaleRows(total);

        int deleted = cleanupService.deleteStaleThrottles();

        assertThat(deleted).isEqualTo(total);
        assertThat(count()).isZero();
    }

    @Test
    @DisplayName("[F-02][PW-05] 지울 것이 없으면 0을 돌려준다")
    void returnsZeroWhenNothingToDelete() {
        assertThat(cleanupService.deleteStaleThrottles()).isZero();

        insertRow(clock.instant());

        assertThat(cleanupService.deleteStaleThrottles()).isZero();
        assertThat(count()).isEqualTo(1);
    }

    private Instant cutoff() {
        return clock.instant().minus(PasswordResetRequestPolicy.STALE_AFTER);
    }

    private String insertRow(Instant lastRequestAt) {
        String key = nextKey();
        jdbc.update(
                "INSERT INTO password_reset_throttle"
                        + " (throttle_key, window_start, request_count, last_request_at, created_at, updated_at)"
                        + " VALUES (?, ?, 1, ?, ?, ?)",
                key,
                DATETIME_UTC.format(lastRequestAt),
                DATETIME_UTC.format(lastRequestAt),
                DATETIME_UTC.format(lastRequestAt),
                DATETIME_UTC.format(lastRequestAt));
        return key;
    }

    private void insertStaleRows(int count) {
        String staleAt = DATETIME_UTC.format(cutoff().minus(ONE_SECOND));
        List<Object[]> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new Object[] {nextKey(), staleAt, staleAt, staleAt, staleAt});
        }
        jdbc.batchUpdate(
                "INSERT INTO password_reset_throttle"
                        + " (throttle_key, window_start, request_count, last_request_at, created_at, updated_at)"
                        + " VALUES (?, ?, 1, ?, ?, ?)",
                rows);
    }

    // 키는 해시이므로 64자 16진수이기만 하면 된다. 행마다 겹치지 않게 순번을 채워 만든다.
    private String nextKey() {
        return "%064x".formatted(keySequence.incrementAndGet());
    }

    private boolean exists(String key) {
        Integer found = jdbc.queryForObject(
                "SELECT COUNT(*) FROM password_reset_throttle WHERE throttle_key = ?", Integer.class, key);
        return found != null && found == 1;
    }

    private int count() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_throttle", Integer.class);
        return count == null ? 0 : count;
    }
}
