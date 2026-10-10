package com.pitchmap.trust.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.testsupport.TestSequence;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class CiRetentionCleanupIntegrationTest {

    private static final Duration ONE_SECOND = Duration.ofSeconds(1);
    // DATETIME 컬럼에 UTC 시각을 문자열로 넣는다. 드라이버의 시간대 변환이 끼어들지 않게 하려는 것이다.
    private static final DateTimeFormatter DATETIME_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC);

    private final AtomicInteger hashSequence = new AtomicInteger();

    @Autowired
    private CiRetentionCleanupService cleanupService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName("[F-11][PV-05] 보관 기한이 지난 행은 지우고, 기한이 아직 안 된 행과 기한이 없는 행(탈퇴하지 않은 회원)은 남긴다")
    void deletesOnlyRowsPastRetention() {
        Instant now = clock.instant();
        long past = insertRow(now.minus(ONE_SECOND));
        long future = insertRow(now.plus(ONE_SECOND));
        long active = insertRow(null);

        int deleted = cleanupService.deleteRetentionExpired();

        assertThat(deleted).isEqualTo(1);
        assertThat(exists(past)).isFalse();
        assertThat(exists(future)).isTrue();
        assertThat(exists(active)).isTrue();
    }

    @Test
    @DisplayName("[F-11][PV-05] 보관 기한이 정확히 지금인 행도 지운다")
    void deletesRowWhoseRetentionEndsNow() {
        long exact = insertRow(clock.instant());

        int deleted = cleanupService.deleteRetentionExpired();

        assertThat(deleted).isEqualTo(1);
        assertThat(exists(exact)).isFalse();
    }

    @Test
    @DisplayName("[F-11][PV-05] 시계가 움직여 보관 기한에 닿으면 그때부터 삭제 대상이 된다")
    void rowBecomesDeletableWhenClockReachesRetention() {
        long id = insertRow(clock.instant().plus(Duration.ofDays(30)));

        assertThat(cleanupService.deleteRetentionExpired()).isZero();
        clock.advance(Duration.ofDays(30).minus(ONE_SECOND));
        assertThat(cleanupService.deleteRetentionExpired()).isZero();
        clock.advance(ONE_SECOND);

        assertThat(cleanupService.deleteRetentionExpired()).isEqualTo(1);
        assertThat(exists(id)).isFalse();
    }

    @Test
    @DisplayName("[F-11][PV-05] 한 번에 지우는 상한보다 대상이 많아도 모두 지운다")
    void deletesAllExpiredRowsBeyondBatchSize() {
        int total = CiRetentionCleanupService.BATCH_SIZE + 1;
        for (int i = 0; i < total; i++) {
            insertRow(clock.instant().minus(ONE_SECOND));
        }
        long active = insertRow(null);

        int deleted = cleanupService.deleteRetentionExpired();

        assertThat(deleted).isEqualTo(total);
        assertThat(exists(active)).isTrue();
        assertThat(count()).isEqualTo(1);
    }

    private long insertRow(Instant retainedUntil) {
        String now = DATETIME_UTC.format(clock.instant());
        jdbc.update(
                "INSERT INTO member (email, password_hash, nickname, status, role, created_at, updated_at)"
                        + " VALUES (?, ?, ?, 'ACTIVE', 'USER', ?, ?)",
                TestSequence.email(),
                "x".repeat(60),
                TestSequence.nickname(),
                now,
                now);
        long memberId = jdbc.queryForObject("SELECT MAX(id) FROM member", Long.class);
        jdbc.update(
                "INSERT INTO identity_verification (member_id, ci_hash, provider, verified_at, ci_retained_until,"
                        + " created_at, updated_at) VALUES (?, ?, 'DEMO', ?, ?, ?, ?)",
                memberId,
                "%064x".formatted(hashSequence.incrementAndGet()),
                now,
                retainedUntil == null ? null : DATETIME_UTC.format(retainedUntil),
                now,
                now);
        return memberId;
    }

    private boolean exists(long memberId) {
        Integer found = jdbc.queryForObject(
                "SELECT COUNT(*) FROM identity_verification WHERE member_id = ?", Integer.class, memberId);
        return found != null && found == 1;
    }

    private int count() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM identity_verification", Integer.class);
        return count == null ? 0 : count;
    }
}
