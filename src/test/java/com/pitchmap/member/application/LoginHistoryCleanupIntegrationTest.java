package com.pitchmap.member.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class LoginHistoryCleanupIntegrationTest {

    private static final Duration ONE_SECOND = Duration.ofSeconds(1);
    // DATETIME 컬럼에 UTC 시각을 문자열로 넣는다. 드라이버의 시간대 변환이 끼어들지 않게 하려는 것이다.
    private static final DateTimeFormatter DATETIME_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC);

    @Autowired
    private LoginHistoryCleanupService cleanupService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName("[F-02][PV-06] 3개월보다 1초 더 오래된 로그인 기록은 지우고, 정확히 3개월 된 기록과 그보다 새 기록은 남긴다")
    void deletesOnlyRecordsOlderThanThreeMonths() {
        Instant cutoff = cutoff();
        long older = insertRecord(cutoff.minus(ONE_SECOND));
        long exact = insertRecord(cutoff);
        long newer = insertRecord(cutoff.plus(ONE_SECOND));

        int deleted = cleanupService.deleteExpired();

        assertThat(deleted).isEqualTo(1);
        assertThat(exists(older)).isFalse();
        assertThat(exists(exact)).isTrue();
        assertThat(exists(newer)).isTrue();
    }

    @Test
    @DisplayName("[F-02][PV-06] 3개월은 일수가 아니라 달력 기준이라, 5월 31일에는 2월 28일보다 오래된 기록만 지운다")
    void cutoffFollowsCalendarMonths() {
        clock.setInstant(Instant.parse("2026-05-31T03:00:00Z"));
        long older = insertRecord(Instant.parse("2026-02-28T02:59:59Z"));
        long exact = insertRecord(Instant.parse("2026-02-28T03:00:00Z"));

        int deleted = cleanupService.deleteExpired();

        assertThat(deleted).isEqualTo(1);
        assertThat(exists(older)).isFalse();
        assertThat(exists(exact)).isTrue();
    }

    @Test
    @DisplayName("[F-02][PV-06] 시계가 움직여 3개월을 넘기면 그때부터 삭제 대상이 된다")
    void recordBecomesDeletableWhenClockPassesRetention() {
        long id = insertRecord(clock.instant());

        assertThat(cleanupService.deleteExpired()).isZero();
        clock.setInstant(clock.instant().atZone(ZoneOffset.UTC).plusMonths(3).toInstant());
        assertThat(cleanupService.deleteExpired()).isZero();
        clock.advance(ONE_SECOND);

        assertThat(cleanupService.deleteExpired()).isEqualTo(1);
        assertThat(exists(id)).isFalse();
    }

    @Test
    @DisplayName("[F-02][PV-06] 한 번에 지우는 상한보다 대상이 많아도 모두 지우고, 새 기록은 남긴다")
    void deletesAllExpiredRowsBeyondBatchSize() {
        int total = LoginHistoryCleanupService.BATCH_SIZE + 1;
        insertExpiredRecords(total);
        long recent = insertRecord(clock.instant());

        int deleted = cleanupService.deleteExpired();

        assertThat(deleted).isEqualTo(total);
        assertThat(count()).isEqualTo(1);
        assertThat(exists(recent)).isTrue();
    }

    @Test
    @DisplayName("[F-02][PV-06] 지울 것이 없으면 0을 돌려준다")
    void returnsZeroWhenNothingToDelete() {
        insertRecord(clock.instant());

        assertThat(cleanupService.deleteExpired()).isZero();
        assertThat(count()).isEqualTo(1);
    }

    private Instant cutoff() {
        return clock.instant()
                .atZone(ZoneOffset.UTC)
                .minus(LoginHistoryCleanupService.RETENTION)
                .toInstant();
    }

    private long insertRecord(Instant createdAt) {
        jdbc.update(
                "INSERT INTO login_history (member_id, attempted_email, ip, success, created_at)"
                        + " VALUES (NULL, 'attempt@example.com', '127.0.0.1', FALSE, ?)",
                DATETIME_UTC.format(createdAt));
        return jdbc.queryForObject("SELECT MAX(id) FROM login_history", Long.class);
    }

    private void insertExpiredRecords(int count) {
        String expiredAt = DATETIME_UTC.format(cutoff().minus(ONE_SECOND));
        List<Object[]> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new Object[] {expiredAt});
        }
        jdbc.batchUpdate(
                "INSERT INTO login_history (member_id, attempted_email, ip, success, created_at)"
                        + " VALUES (NULL, 'attempt@example.com', '127.0.0.1', FALSE, ?)",
                rows);
    }

    private boolean exists(long id) {
        Integer found = jdbc.queryForObject("SELECT COUNT(*) FROM login_history WHERE id = ?", Integer.class, id);
        return found != null && found == 1;
    }

    private int count() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM login_history", Integer.class);
        return count == null ? 0 : count;
    }
}
