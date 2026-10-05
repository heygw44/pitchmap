package com.pitchmap.member.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.domain.PasswordResetThrottle;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class PasswordResetThrottleMapperIntegrationTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;
    private static final String KEY = "a".repeat(64);
    private static final String OTHER_KEY = "b".repeat(64);

    @Autowired
    private PasswordResetThrottleMapper mapper;

    @Autowired
    private PasswordResetThrottleJpaRepository repository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("[F-02][PW-05] insertIfAbsent는 새 키에 요청 0번인 행을 한 개 만든다")
    void insertIfAbsentCreatesRowForNewKey() {
        // when
        int inserted = mapper.insertIfAbsent(KEY, NOW);

        // then
        assertThat(inserted).isEqualTo(1);
        assertThat(countRows()).isEqualTo(1);
        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM password_reset_throttle");
        assertThat(row.get("throttle_key")).isEqualTo(KEY);
        assertThat(toInstant(row.get("window_start"))).isEqualTo(NOW);
        assertThat(((Number) row.get("request_count")).intValue()).isZero();
        assertThat(row.get("last_request_at")).isNull();
        assertThat(toInstant(row.get("created_at"))).isEqualTo(NOW);
        assertThat(toInstant(row.get("updated_at"))).isEqualTo(NOW);
    }

    @Test
    @DisplayName("[F-02][PW-05] insertIfAbsent는 이미 있는 키의 값을 바꾸지 않고 행도 늘리지 않는다")
    void insertIfAbsentKeepsExistingRow() {
        // given
        insertRow(KEY, NOW, 3, NOW.plusSeconds(10));

        // when
        mapper.insertIfAbsent(KEY, NOW.plusSeconds(500));

        // then
        assertThat(countRows()).isEqualTo(1);
        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM password_reset_throttle");
        assertThat(((Number) row.get("request_count")).intValue()).isEqualTo(3);
        assertThat(toInstant(row.get("window_start"))).isEqualTo(NOW);
        assertThat(toInstant(row.get("last_request_at"))).isEqualTo(NOW.plusSeconds(10));
        assertThat(toInstant(row.get("created_at"))).isEqualTo(NOW);
        assertThat(toInstant(row.get("updated_at"))).isEqualTo(NOW);
    }

    @Test
    @DisplayName("[F-02][PW-05] findByKeyForUpdate는 저장된 값을 읽고, 없는 키는 빈 값을 돌려준다")
    void findByKeyForUpdateReadsStoredValues() {
        // given
        Instant lastRequestAt = NOW.plusSeconds(10);
        insertRow(KEY, NOW, 3, lastRequestAt);

        // when
        PasswordResetThrottle found = transactionTemplate.execute(
                status -> repository.findByKeyForUpdate(KEY).orElseThrow());
        boolean otherExists = Boolean.TRUE.equals(transactionTemplate.execute(
                status -> repository.findByKeyForUpdate(OTHER_KEY).isPresent()));

        // then
        assertThat(found.getKey()).isEqualTo(KEY);
        assertThat(found.getWindowStart()).isEqualTo(NOW);
        assertThat(found.getRequestCount()).isEqualTo(3);
        assertThat(found.getLastRequestAt()).isEqualTo(lastRequestAt);
        assertThat(found.getCreatedAt()).isEqualTo(NOW);
        assertThat(found.getUpdatedAt()).isEqualTo(NOW);
        assertThat(otherExists).isFalse();
    }

    @Test
    @DisplayName("[F-02][PW-05] insertIfAbsent로 만든 행은 마지막 요청 시각이 비어 있어도 읽힌다")
    void rowCreatedByMapperIsReadable() {
        // given
        mapper.insertIfAbsent(KEY, NOW);

        // when
        PasswordResetThrottle found = transactionTemplate.execute(
                status -> repository.findByKeyForUpdate(KEY).orElseThrow());

        // then
        assertThat(found.getRequestCount()).isZero();
        assertThat(found.getLastRequestAt()).isNull();
        assertThat(found.getWindowStart()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("[F-02][PW-05] deleteStale은 마지막 요청이 cutoff보다 이른 행만 지운다")
    void deleteStaleRemovesOnlyRowsBeforeCutoff() {
        // given
        Instant cutoff = NOW;
        insertRow("1".repeat(64), NOW.minusSeconds(86400), 5, cutoff.minusSeconds(1));
        insertRow("2".repeat(64), NOW.minusSeconds(86400), 5, cutoff);
        insertRow("3".repeat(64), NOW.minusSeconds(86400), 5, cutoff.plusSeconds(1));
        insertRow("4".repeat(64), NOW.minusSeconds(86400), 0, null);

        // when
        int deleted = mapper.deleteStale(cutoff, 100);

        // then
        assertThat(deleted).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList("SELECT throttle_key FROM password_reset_throttle", String.class))
                .containsExactlyInAnyOrder("2".repeat(64), "3".repeat(64), "4".repeat(64));
    }

    @Test
    @DisplayName("[F-02][PW-05] deleteStale은 limit개까지만 지우고 지운 수를 돌려준다")
    void deleteStaleHonorsLimit() {
        // given
        for (int i = 0; i < 5; i++) {
            insertRow(String.valueOf(i).repeat(64), NOW.minusSeconds(200000), 1, NOW.minusSeconds(100000));
        }

        // when
        int firstBatch = mapper.deleteStale(NOW, 3);
        int secondBatch = mapper.deleteStale(NOW, 3);
        int thirdBatch = mapper.deleteStale(NOW, 3);

        // then
        assertThat(firstBatch).isEqualTo(3);
        assertThat(secondBatch).isEqualTo(2);
        assertThat(thirdBatch).isZero();
        assertThat(countRows()).isZero();
    }

    @Test
    @DisplayName("[F-02][PW-06] 마이그레이션이 member.password_changed_at 칼럼을 만든다")
    void migrationAddsPasswordChangedAtColumn() {
        // when
        Integer columns = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'member' "
                        + "AND column_name = 'password_changed_at' AND is_nullable = 'YES'",
                Integer.class);

        // then
        assertThat(columns).isEqualTo(1);
    }

    private void insertRow(String key, Instant windowStart, int requestCount, Instant lastRequestAt) {
        jdbcTemplate.update(
                "INSERT INTO password_reset_throttle "
                        + "(throttle_key, window_start, request_count, last_request_at, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                key,
                toDateTime(windowStart),
                requestCount,
                lastRequestAt == null ? null : toDateTime(lastRequestAt),
                toDateTime(NOW),
                toDateTime(NOW));
    }

    private int countRows() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM password_reset_throttle", Integer.class);
    }

    // DATETIME 컬럼에는 UTC 시각이 들어 있으므로(connectionTimeZone=UTC) LocalDateTime을 UTC로 바꿔 넣고 읽는다.
    private static LocalDateTime toDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant toInstant(Object dateTime) {
        return ((LocalDateTime) dateTime).toInstant(ZoneOffset.UTC);
    }
}
