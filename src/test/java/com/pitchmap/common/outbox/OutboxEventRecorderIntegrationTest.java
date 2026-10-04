package com.pitchmap.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.testsupport.IntegrationTest;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class OutboxEventRecorderIntegrationTest {

    private static final String EVENT_TYPE = "TEST_EVENT";
    private static final String AGGREGATE_TYPE = "TEST_AGGREGATE";
    private static final long AGGREGATE_ID = 7L;
    private static final SamplePayload PAYLOAD = new SamplePayload(42L, "welcome");

    @Autowired
    private OutboxEventRecorder recorder;

    @Autowired
    private OutboxEventJpaRepository repository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private Clock clock;

    @Test
    @DisplayName("[NFR-04] 트랜잭션 안에서 기록하면 시도 횟수 0인 PENDING 행이 저장되고 payload를 JSON으로 다시 읽을 수 있다")
    void recordInTransaction_savesPendingRowWithReadablePayload() {
        // given & when
        transactionTemplate.executeWithoutResult(
                status -> recorder.record(EVENT_TYPE, AGGREGATE_TYPE, AGGREGATE_ID, PAYLOAD));

        // then
        List<OutboxEvent> events = repository.findAll();
        assertThat(events).hasSize(1);
        OutboxEvent event = events.get(0);
        assertThat(event.getEventType()).isEqualTo(EVENT_TYPE);
        assertThat(event.getAggregateType()).isEqualTo(AGGREGATE_TYPE);
        assertThat(event.getAggregateId()).isEqualTo(AGGREGATE_ID);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getAttemptCount()).isZero();
        assertThat(event.getLastError()).isNull();
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getLockedUntil()).isNull();
        assertThat(event.getCreatedAt()).isEqualTo(clock.instant());
        assertThat(event.getUpdatedAt()).isEqualTo(clock.instant());
        assertThat(jsonMapper.readValue(event.getPayload(), SamplePayload.class))
                .isEqualTo(PAYLOAD);
    }

    @Test
    @DisplayName("[NFR-04] 같은 트랜잭션에서 이벤트 두 개를 기록하면 서로 다른 ID로 기록한 순서대로 저장된다")
    void recordTwoEvents_assignsDistinctIdsInInsertionOrder() {
        // given & when
        transactionTemplate.executeWithoutResult(status -> {
            recorder.record(EVENT_TYPE, AGGREGATE_TYPE, 1L, new SamplePayload(1L, "first"));
            recorder.record(EVENT_TYPE, AGGREGATE_TYPE, 2L, new SamplePayload(2L, "second"));
        });

        // then
        List<OutboxEvent> events = repository.findAll(Sort.by("id"));
        assertThat(events).hasSize(2);
        assertThat(events.get(0).getId()).isLessThan(events.get(1).getId());
        assertThat(events).extracting(OutboxEvent::getAggregateId).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("[NFR-04] 활성 트랜잭션 없이 기록하면 IllegalStateException이 나고 행이 저장되지 않는다")
    void recordOutsideTransaction_throwsAndSavesNothing() {
        // given & when & then
        assertThatThrownBy(() -> recorder.record(EVENT_TYPE, AGGREGATE_TYPE, AGGREGATE_ID, PAYLOAD))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("[NFR-04] 원래 작업의 트랜잭션이 롤백되면 기록한 이벤트도 남지 않는다")
    void rollbackOfSurroundingTransaction_leavesNoRow() {
        // given
        Runnable originalWork = () -> {
            recorder.record(EVENT_TYPE, AGGREGATE_TYPE, AGGREGATE_ID, PAYLOAD);
            throw new IllegalArgumentException("원래 작업이 실패했다");
        };

        // when
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> originalWork.run()))
                .isInstanceOf(IllegalArgumentException.class);

        // then
        assertThat(repository.count()).isZero();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("[NFR-04] 이벤트 종류가 null이거나 비어 있으면 IllegalArgumentException이 나고 행이 저장되지 않는다")
    void blankEventType_isRejected(String eventType) {
        // given & when & then
        assertThatThrownBy(() -> recordInTransaction(eventType, AGGREGATE_TYPE, PAYLOAD))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.count()).isZero();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("[NFR-04] 집합체 종류가 null이거나 비어 있으면 IllegalArgumentException이 나고 행이 저장되지 않는다")
    void blankAggregateType_isRejected(String aggregateType) {
        // given & when & then
        assertThatThrownBy(() -> recordInTransaction(EVENT_TYPE, aggregateType, PAYLOAD))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("[NFR-04] payload가 null이면 IllegalArgumentException이 나고 행이 저장되지 않는다")
    void nullPayload_isRejected() {
        // given & when & then
        assertThatThrownBy(() -> recordInTransaction(EVENT_TYPE, AGGREGATE_TYPE, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("[NFR-04] payload를 JSON으로 바꿀 수 없으면 payload 값을 메시지에 싣지 않고 IllegalArgumentException을 던진다")
    void unserializablePayload_isRejectedWithoutEchoingPayload() {
        // given
        UnserializablePayload payload = new UnserializablePayload();

        // when & then
        assertThatThrownBy(() -> recordInTransaction(EVENT_TYPE, AGGREGATE_TYPE, payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining(UnserializablePayload.SENSITIVE_TEXT);
        assertThat(repository.count()).isZero();
    }

    private void recordInTransaction(String eventType, String aggregateType, Object payload) {
        transactionTemplate.executeWithoutResult(
                status -> recorder.record(eventType, aggregateType, AGGREGATE_ID, payload));
    }

    public record SamplePayload(long memberId, String note) {}

    public static class UnserializablePayload {

        static final String SENSITIVE_TEXT = "payload-value-must-not-leak";

        public String getValue() {
            throw new IllegalStateException(SENSITIVE_TEXT);
        }
    }
}
