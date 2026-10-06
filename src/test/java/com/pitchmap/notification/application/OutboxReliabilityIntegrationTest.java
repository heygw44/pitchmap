package com.pitchmap.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.outbox.OutboxEventRecorder;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class OutboxReliabilityIntegrationTest {

    private static final String AGGREGATE_TYPE = "TEST_AGGREGATE";
    private static final String UNKNOWN_EVENT_TYPE = "UNKNOWN_EVENT";
    private static final int CONCURRENT_EVENT_COUNT = 20;
    private static final int CONCURRENT_PUBLISHER_COUNT = 2;
    private static final long THREAD_TIMEOUT_SECONDS = 30;

    @Autowired
    private OutboxPublisher publisher;

    @Autowired
    private OutboxEventStore store;

    @Autowired
    private OutboxEventRecorder recorder;

    @Autowired
    private OutboxProperties properties;

    @Autowired
    private RecordingOutboxEventHandler handler;

    @Autowired
    private MutableClock clock;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 통합 테스트는 Spring 컨텍스트를 공유한다. 확장은 DB 행만 지우므로, 처리기의 기록은 테스트가 직접 지운다.
    @BeforeEach
    void resetHandler() {
        handler.reset();
    }

    @Test
    @DisplayName("[NFR-04] 원래 작업의 트랜잭션이 롤백되면 outbox_event 행이 남지 않고 처리기도 호출되지 않는다")
    void rolledBackTransactionLeavesNoEventAndNeverCallsHandler() {
        // when
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                    recorder.record(RecordingOutboxEventHandler.EVENT_TYPE, AGGREGATE_TYPE, 1L, Map.of("sequence", 1));
                    throw new IllegalStateException("원래 작업 실패");
                }))
                .isInstanceOf(IllegalStateException.class);
        int processed = publisher.publishPending();

        // then
        assertThat(countEvents()).isZero();
        assertThat(processed).isZero();
        assertThat(handler.callCount()).isZero();
    }

    @Test
    @DisplayName("[NFR-04] 처리기가 예외를 던지면 이벤트는 PENDING으로 남고, 재시도 간격이 지난 뒤 다음 발행에서 처리돼 PUBLISHED가 된다")
    void eventStaysPendingWhenHandlerFailsAndIsPublishedAfterRetryDelay() {
        // given
        long eventId = recordEvents(1, RecordingOutboxEventHandler.EVENT_TYPE).get(0);
        handler.failNextCalls(1);

        // when
        int firstRun = publisher.publishPending();

        // then
        assertThat(firstRun).isEqualTo(1);
        Map<String, Object> afterFailure = findEvent(eventId);
        assertThat(afterFailure.get("status")).isEqualTo("PENDING");
        assertThat(afterFailure.get("attempt_count")).isEqualTo(1);
        assertThat(afterFailure.get("last_error"))
                .isEqualTo("IllegalStateException: " + RecordingOutboxEventHandler.FAILURE_MESSAGE);

        // when
        int beforeRetryDelay = publisher.publishPending();

        // then
        assertThat(beforeRetryDelay).isZero();
        assertThat(handler.callCount()).isEqualTo(1);

        // when
        clock.advance(properties.retryDelay());
        int afterRetryDelay = publisher.publishPending();

        // then
        assertThat(afterRetryDelay).isEqualTo(1);
        Map<String, Object> afterRetry = findEvent(eventId);
        assertThat(afterRetry.get("status")).isEqualTo("PUBLISHED");
        assertThat(afterRetry.get("published_at")).isNotNull();
        assertThat(handler.calledEventIds()).containsExactly(eventId, eventId);
    }

    @Test
    @DisplayName("[NFR-04] 재시도 한도(maxAttempts)만큼 실패하면 이벤트는 FAILED가 되고 더 이상 처리되지 않는다")
    void eventBecomesFailedWhenAttemptsReachMaxAttempts() {
        // given
        int maxAttempts = properties.maxAttempts();
        long eventId = recordEvents(1, RecordingOutboxEventHandler.EVENT_TYPE).get(0);
        handler.failNextCalls(maxAttempts);

        // when
        for (int attempt = 1; attempt < maxAttempts; attempt++) {
            publisher.publishPending();
            clock.advance(properties.retryDelay());
        }

        // then
        assertThat(findEvent(eventId).get("status")).isEqualTo("PENDING");

        // when
        publisher.publishPending();
        clock.advance(properties.retryDelay());
        int afterLimit = publisher.publishPending();

        // then
        Map<String, Object> failed = findEvent(eventId);
        assertThat(failed.get("status")).isEqualTo("FAILED");
        assertThat(failed.get("attempt_count")).isEqualTo(maxAttempts);
        assertThat(afterLimit).isZero();
        assertThat(handler.callCount()).isEqualTo(maxAttempts);
    }

    @Test
    @DisplayName("[NFR-04] 이벤트 종류에 맞는 처리기가 없으면 시도 횟수를 올리고 오류를 기록한다")
    void missingHandlerIsRecordedAsFailedAttempt() {
        // given
        long eventId = recordEvents(1, UNKNOWN_EVENT_TYPE).get(0);

        // when
        int processed = publisher.publishPending();

        // then
        assertThat(processed).isEqualTo(1);
        Map<String, Object> event = findEvent(eventId);
        assertThat(event.get("status")).isEqualTo("PENDING");
        assertThat(event.get("attempt_count")).isEqualTo(1);
        assertThat(event.get("last_error")).isEqualTo("no handler for " + UNKNOWN_EVENT_TYPE);
        assertThat(handler.callCount()).isZero();
    }

    @RepeatedTest(5)
    @DisplayName("[NFR-04] 발행기 두 개가 동시에 발행해도 이벤트마다 처리기는 한 번만 호출된다")
    void eachEventIsHandledOnceWhenTwoPublishersRunConcurrently() throws Exception {
        // given
        List<Long> eventIds = recordEvents(CONCURRENT_EVENT_COUNT, RecordingOutboxEventHandler.EVENT_TYPE);
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_PUBLISHER_COUNT);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_PUBLISHER_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();

        // when
        try {
            for (int i = 0; i < CONCURRENT_PUBLISHER_COUNT; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return publisher.publishPending();
                }));
            }
            ready.await(THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            start.countDown();
            int processed = 0;
            for (Future<Integer> result : results) {
                processed += result.get(THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }

            // then
            assertThat(processed).isEqualTo(CONCURRENT_EVENT_COUNT);
        } finally {
            executor.shutdownNow();
        }
        assertThat(handler.calledEventIds()).containsExactlyInAnyOrderElementsOf(eventIds);
        assertThat(countEventsByStatus("PUBLISHED")).isEqualTo(CONCURRENT_EVENT_COUNT);
        assertThat(sumAttemptCount()).isZero();
    }

    @Test
    @DisplayName("[NFR-04] 임대가 끝나지 않은 이벤트는 다른 발행기가 집지 않고, 임대 시간이 지나면 다시 집는다")
    void leasedEventIsClaimedAgainOnlyAfterLeaseExpires() {
        // given
        long eventId = recordEvents(1, RecordingOutboxEventHandler.EVENT_TYPE).get(0);
        // 처리기를 호출하는 도중에 다른 발행기가 같은 이벤트를 만난 상황이다. 집기만 하고 처리하지 않은 채로 둔다.
        assertThat(store.claim(clock.instant())).hasSize(1);

        // when
        clock.advance(properties.lease().minusSeconds(1));
        int beforeExpiry = publisher.publishPending();

        // then
        assertThat(beforeExpiry).isZero();
        assertThat(handler.callCount()).isZero();

        // when
        clock.advance(Duration.ofSeconds(1));
        int afterExpiry = publisher.publishPending();

        // then
        assertThat(afterExpiry).isEqualTo(1);
        assertThat(handler.calledEventIds()).containsExactly(eventId);
        assertThat(findEvent(eventId).get("status")).isEqualTo("PUBLISHED");
    }

    private List<Long> recordEvents(int count, String eventType) {
        transactionTemplate.executeWithoutResult(status -> {
            for (int sequence = 1; sequence <= count; sequence++) {
                recorder.record(eventType, AGGREGATE_TYPE, sequence, Map.of("sequence", sequence));
            }
        });
        return jdbcTemplate.queryForList("SELECT id FROM outbox_event ORDER BY id", Long.class);
    }

    private Map<String, Object> findEvent(long eventId) {
        return jdbcTemplate.queryForMap(
                "SELECT status, attempt_count, last_error, published_at, locked_until FROM outbox_event WHERE id = ?",
                eventId);
    }

    private int countEvents() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_event", Integer.class);
    }

    private int countEventsByStatus(String status) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE status = ?", Integer.class, status);
    }

    private int sumAttemptCount() {
        return jdbcTemplate.queryForObject("SELECT COALESCE(SUM(attempt_count), 0) FROM outbox_event", Integer.class);
    }
}
