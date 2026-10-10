package com.pitchmap.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class OutboxEventCleanupIntegrationTest {

    private static final Duration ONE_SECOND = Duration.ofSeconds(1);
    // DATETIME 컬럼에 UTC 시각을 문자열로 넣는다. 드라이버의 시간대 변환이 끼어들지 않게 하려는 것이다.
    private static final DateTimeFormatter DATETIME_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC);

    @Autowired
    private OutboxEventCleaner cleaner;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName("[NFR-04][PV-06] 발행한 지 7일보다 1초 더 지난 이벤트는 지우고, 정확히 7일 된 이벤트와 그보다 새 이벤트는 남긴다")
    void deletesOnlyPublishedEventsOlderThanRetention() {
        Instant cutoff = clock.instant().minus(OutboxEventCleaner.RETENTION);
        long older = insertEvent("PUBLISHED", cutoff.minus(ONE_SECOND));
        long exact = insertEvent("PUBLISHED", cutoff);
        long newer = insertEvent("PUBLISHED", cutoff.plus(ONE_SECOND));

        int deleted = cleaner.deletePublished();

        assertThat(deleted).isEqualTo(1);
        assertThat(exists(older)).isFalse();
        assertThat(exists(exact)).isTrue();
        assertThat(exists(newer)).isTrue();
    }

    @Test
    @DisplayName("[NFR-04] 오래된 PENDING 이벤트와 FAILED 이벤트는 지우지 않는다")
    void keepsOldPendingAndFailedEvents() {
        Instant old = clock.instant().minus(Duration.ofDays(30));
        long pending = insertEvent("PENDING", null, old);
        long failed = insertEvent("FAILED", null, old);
        long published = insertEvent("PUBLISHED", old);

        int deleted = cleaner.deletePublished();

        assertThat(deleted).isEqualTo(1);
        assertThat(exists(pending)).isTrue();
        assertThat(exists(failed)).isTrue();
        assertThat(exists(published)).isFalse();
    }

    @Test
    @DisplayName("[NFR-04] 한 번에 지우는 상한보다 대상이 많아도 모두 지운다")
    void deletesAllExpiredEventsBeyondBatchSize() {
        int total = OutboxEventCleaner.BATCH_SIZE + 1;
        Instant old = clock.instant().minus(Duration.ofDays(30));
        for (int i = 0; i < total; i++) {
            insertEvent("PUBLISHED", old);
        }
        long recent = insertEvent("PUBLISHED", clock.instant());

        int deleted = cleaner.deletePublished();

        assertThat(deleted).isEqualTo(total);
        assertThat(exists(recent)).isTrue();
    }

    private long insertEvent(String status, Instant publishedAt) {
        return insertEvent(status, publishedAt, publishedAt);
    }

    private long insertEvent(String status, Instant publishedAt, Instant createdAt) {
        jdbc.update(
                "INSERT INTO outbox_event (event_type, aggregate_type, aggregate_id, payload, status, published_at,"
                        + " created_at, updated_at) VALUES ('TEST_EVENT', 'TEST', 1, '{}', ?, ?, ?, ?)",
                status,
                publishedAt == null ? null : DATETIME_UTC.format(publishedAt),
                DATETIME_UTC.format(createdAt),
                DATETIME_UTC.format(createdAt));
        return jdbc.queryForObject("SELECT MAX(id) FROM outbox_event", Long.class);
    }

    private boolean exists(long id) {
        Integer found = jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE id = ?", Integer.class, id);
        return found != null && found == 1;
    }
}
