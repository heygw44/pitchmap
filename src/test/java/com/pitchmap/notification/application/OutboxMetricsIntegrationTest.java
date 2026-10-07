package com.pitchmap.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.outbox.OutboxEventRecorder;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class OutboxMetricsIntegrationTest {

    private static final String AGGREGATE_TYPE = "TEST_AGGREGATE";

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private OutboxEventRecorder recorder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName("[NFR-09] 미발행 이벤트가 없으면 미발행 수, 실패 수, 가장 오래된 미발행 이벤트의 나이가 모두 0이다")
    void reportsZeroWhenNothingIsPending() {
        assertThat(events("pending")).isZero();
        assertThat(events("failed")).isZero();
        assertThat(oldestPendingAgeSeconds()).isZero();
    }

    @Test
    @DisplayName("[NFR-09] 상태별 이벤트 수와 가장 오래된 미발행 이벤트가 기록된 뒤 지난 시간을 지표로 읽는다")
    void reportsBacklogFromDatabase() {
        // given: 이벤트 3건을 기록하고 1건은 재시도 한도를 넘긴 것처럼 FAILED로 바꾼다
        transactionTemplate.executeWithoutResult(status -> {
            for (int sequence = 1; sequence <= 3; sequence++) {
                recorder.record(
                        RecordingOutboxEventHandler.EVENT_TYPE, AGGREGATE_TYPE, sequence, Map.of("sequence", sequence));
            }
        });
        jdbcTemplate.update(
                "UPDATE outbox_event SET status = 'FAILED' WHERE id = (SELECT id FROM (SELECT MIN(id) AS id FROM outbox_event) t)");

        // when
        clock.advance(Duration.ofMinutes(15));

        // then
        assertThat(events("pending")).isEqualTo(2);
        assertThat(events("failed")).isEqualTo(1);
        assertThat(oldestPendingAgeSeconds()).isEqualTo(900);
    }

    @Test
    @DisplayName("[NFR-09] 발행을 마친 이벤트는 미발행 수와 나이에 들어가지 않는다")
    void ignoresPublishedEvents() {
        transactionTemplate.executeWithoutResult(status ->
                recorder.record(RecordingOutboxEventHandler.EVENT_TYPE, AGGREGATE_TYPE, 1L, Map.of("sequence", 1)));
        jdbcTemplate.update("UPDATE outbox_event SET status = 'PUBLISHED'");
        clock.advance(Duration.ofHours(1));

        assertThat(events("pending")).isZero();
        assertThat(oldestPendingAgeSeconds()).isZero();
    }

    private double events(String status) {
        return meterRegistry
                .get("pitchmap.outbox.events")
                .tag("status", status)
                .gauge()
                .value();
    }

    private double oldestPendingAgeSeconds() {
        return meterRegistry.get("pitchmap.outbox.oldest.pending.age").gauge().value();
    }
}
