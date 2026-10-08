package com.pitchmap.notification.application;

import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.outbox.OutboxEventRecorder;
import com.pitchmap.common.outbox.OutboxMessage;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.infra.MemberJpaRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class NotificationReliabilityIntegrationTest {

    private static final String AGGREGATE_TYPE = "BASECAMP";
    private static final String APPROVED = "BASECAMP_APPROVED";
    private static final int CONCURRENT_EVENT_COUNT = 20;
    private static final int CONCURRENT_PUBLISHER_COUNT = 2;
    private static final long THREAD_TIMEOUT_SECONDS = 30;

    @Autowired
    private OutboxPublisher publisher;

    @Autowired
    private OutboxEventRecorder recorder;

    @Autowired
    private BasecampApprovedNotificationHandler approvedHandler;

    @Autowired
    private NotificationWriter writer;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName("[F-20][NFR-04] 같은 이벤트를 처리기가 두 번 처리해도 알림은 한 건이다")
    void handlerCalledTwiceWithSameEventCreatesOneNotification() {
        // given
        long applicantId = saveMember();
        OutboxMessage message = new OutboxMessage(
                9001L, APPROVED, AGGREGATE_TYPE, 3L, approvedPayload(applicantId, 3L), clock.instant());

        // when
        approvedHandler.handle(message);
        approvedHandler.handle(message);

        // then
        assertThat(countNotifications()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT dedup_key FROM notification", String.class))
                .isEqualTo("9001:" + applicantId);
    }

    @Test
    @DisplayName("[F-20][NFR-04] 발행을 마친 이벤트를 PENDING으로 되돌려 다시 발행해도 알림은 한 건이다")
    void republishingPublishedEventCreatesOneNotification() {
        // given
        long applicantId = saveMember();
        long eventId = recordApproved(applicantId, 3L);

        // when
        int first = publisher.publishPending();
        jdbcTemplate.update(
                "UPDATE outbox_event SET status = 'PENDING', published_at = NULL, locked_until = NULL WHERE id = ?",
                eventId);
        int second = publisher.publishPending();

        // then
        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(1);
        assertThat(countNotifications()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM outbox_event WHERE id = ?", String.class, eventId))
                .isEqualTo("PUBLISHED");
    }

    @RepeatedTest(5)
    @DisplayName("[F-20][NFR-04] 발행기 두 개가 동시에 발행해도 이벤트 20건은 알림 20건이 되고 중복 방지 키는 겹치지 않는다")
    void twoPublishersCreateOneNotificationPerEvent() throws Exception {
        // given
        List<Long> applicantIds = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_EVENT_COUNT; i++) {
            applicantIds.add(saveMember());
        }
        transactionTemplate.executeWithoutResult(status -> {
            for (int i = 0; i < applicantIds.size(); i++) {
                recordApprovedEvent(applicantIds.get(i), i + 1L);
            }
        });
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
            for (Future<Integer> result : results) {
                result.get(THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        // then
        assertThat(countNotifications()).isEqualTo(CONCURRENT_EVENT_COUNT);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT dedup_key) FROM notification", Integer.class))
                .isEqualTo(CONCURRENT_EVENT_COUNT);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM outbox_event WHERE status = 'PUBLISHED'", Integer.class))
                .isEqualTo(CONCURRENT_EVENT_COUNT);
    }

    @Test
    @DisplayName("[F-20][NFR-04] 원래 작업의 트랜잭션이 롤백되면 이벤트가 남지 않아 알림도 생기지 않는다")
    void rolledBackOriginalWorkCreatesNoNotification() {
        // given
        long applicantId = saveMember();

        // when
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                    recordApprovedEvent(applicantId, 3L);
                    throw new IllegalStateException("원래 작업 실패");
                }))
                .isInstanceOf(IllegalStateException.class);
        publisher.publishPending();

        // then
        assertThat(countNotifications()).isZero();
    }

    @Test
    @DisplayName("[F-20][NFR-04] 알림을 저장하는 트랜잭션이 롤백되면 알림이 남지 않는다")
    void rolledBackWriteLeavesNoNotification() {
        // given
        long applicantId = saveMember();
        List<NotificationDraft> drafts = List.of(new NotificationDraft(applicantId, APPROVED, "제목", "본문", null));

        // when
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                    writer.write(9002L, drafts);
                    throw new IllegalStateException("저장 뒤 실패");
                }))
                .isInstanceOf(IllegalStateException.class);

        // then
        assertThat(countNotifications()).isZero();
    }

    private long saveMember() {
        return memberRepository.saveAndFlush(aMember().build()).getId();
    }

    private long recordApproved(long applicantId, long basecampId) {
        transactionTemplate.executeWithoutResult(status -> recordApprovedEvent(applicantId, basecampId));
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM outbox_event", Long.class);
    }

    // 호출하는 쪽의 트랜잭션 안에서 부른다.
    private void recordApprovedEvent(long applicantId, long basecampId) {
        recorder.record(
                APPROVED,
                AGGREGATE_TYPE,
                basecampId,
                Map.of("applicationId", 1L, "applicantId", applicantId, "basecampId", basecampId));
    }

    private static String approvedPayload(long applicantId, long basecampId) {
        return "{\"applicationId\":1,\"applicantId\":%d,\"basecampId\":%d}".formatted(applicantId, basecampId);
    }

    private int countNotifications() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification", Integer.class);
    }
}
