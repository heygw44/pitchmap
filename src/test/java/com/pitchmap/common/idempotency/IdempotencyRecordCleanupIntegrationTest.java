package com.pitchmap.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.testsupport.TestSequence;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class IdempotencyRecordCleanupIntegrationTest {

    private static final Duration ONE_SECOND = Duration.ofSeconds(1);
    // DATETIME 컬럼에 UTC 시각을 문자열로 넣는다. 드라이버의 시간대 변환이 끼어들지 않게 하려는 것이다.
    private static final DateTimeFormatter DATETIME_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC);

    @Autowired
    private IdempotencyRecordCleaner cleaner;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName("[NFR-03] 만든 지 하루보다 1초 더 지난 기록은 지우고, 정확히 하루 된 기록과 그보다 새 기록은 남긴다")
    void deletesOnlyRecordsOlderThanRetention() {
        long memberId = insertMember();
        Instant cutoff = clock.instant().minus(IdempotencyRecordCleaner.RETENTION);
        insertRecord(memberId, "older", cutoff.minus(ONE_SECOND));
        insertRecord(memberId, "exact", cutoff);
        insertRecord(memberId, "newer", cutoff.plus(ONE_SECOND));

        int deleted = cleaner.deleteExpired();

        assertThat(deleted).isEqualTo(1);
        assertThat(exists(memberId, "older")).isFalse();
        assertThat(exists(memberId, "exact")).isTrue();
        assertThat(exists(memberId, "newer")).isTrue();
    }

    @Test
    @DisplayName("[NFR-03] 시계가 움직여 하루를 넘기면 그때부터 삭제 대상이 된다")
    void recordBecomesDeletableWhenClockPassesRetention() {
        long memberId = insertMember();
        insertRecord(memberId, "key-1", clock.instant());

        assertThat(cleaner.deleteExpired()).isZero();
        clock.advance(IdempotencyRecordCleaner.RETENTION);
        assertThat(cleaner.deleteExpired()).isZero();
        clock.advance(ONE_SECOND);

        assertThat(cleaner.deleteExpired()).isEqualTo(1);
        assertThat(exists(memberId, "key-1")).isFalse();
    }

    private long insertMember() {
        String now = DATETIME_UTC.format(clock.instant());
        jdbc.update(
                "INSERT INTO member (email, password_hash, nickname, status, role, created_at, updated_at)"
                        + " VALUES (?, ?, ?, 'UNVERIFIED', 'USER', ?, ?)",
                TestSequence.email(),
                "x".repeat(60),
                TestSequence.nickname(),
                now,
                now);
        return jdbc.queryForObject("SELECT MAX(id) FROM member", Long.class);
    }

    private void insertRecord(long memberId, String key, Instant createdAt) {
        jdbc.update(
                "INSERT INTO idempotency_record (member_id, idempotency_key, request_hash, created_at)"
                        + " VALUES (?, ?, ?, ?)",
                memberId,
                key,
                "a".repeat(64),
                DATETIME_UTC.format(createdAt));
    }

    private boolean exists(long memberId, String key) {
        Integer found = jdbc.queryForObject(
                "SELECT COUNT(*) FROM idempotency_record WHERE member_id = ? AND idempotency_key = ?",
                Integer.class,
                memberId,
                key);
        return found != null && found == 1;
    }
}
