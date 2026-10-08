package com.pitchmap.basecamp.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.basecamp.domain.BasecampErrorCode;
import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.member.infra.MemberJpaRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class BasecampAutoTransitionConcurrencyIntegrationTest {

    private static final Instant AUTO_NOW = Instant.parse("2026-10-18T15:00:00Z");
    private static final LocalDate AFTER_END = BasecampAutoTransitionFixture.END_DATE.plusDays(1);

    @Autowired
    private BasecampAutoTransitionApplier applier;

    @Autowired
    private BasecampTransitionService transitionService;

    @Autowired
    private BasecampMembershipService membershipService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JsonMapper jsonMapper;

    private BasecampAutoTransitionFixture fixture;

    @BeforeEach
    void createFixture() {
        fixture = new BasecampAutoTransitionFixture(jdbc, memberRepository, jsonMapper);
    }

    @RepeatedTest(5)
    @DisplayName("[F-14][NFR-10] 캠프 리더의 확정과 자동 처리가 동시에 오면 한 번만 확정하고, 진 쪽은 BASECAMP_INVALID_STATE이거나 처리할 것이 없다는 응답이다")
    void leaderConfirmAndAutoProcessConfirmOnce() throws Exception {
        long basecampId = fixture.saveBasecampWithMembers("RECRUITING", 1);
        long leaderId = fixture.leaderIdOf(basecampId);
        AtomicInteger autoProcessed = new AtomicInteger();

        List<Throwable> failures = runConcurrently(List.of(
                () -> transitionService.confirm(basecampId, leaderId),
                () -> countIfTrue(applier.processDayBeforeDeparture(basecampId, AUTO_NOW), autoProcessed)));

        assertThat(fixture.statusOf(basecampId)).isEqualTo("CONFIRMED");
        assertThat(fixture.eventCount(BasecampTransitionEvents.CONFIRMED_EVENT_TYPE, basecampId))
                .isEqualTo(1);
        assertThat(fixture.eventCount(BasecampTransitionEvents.CANCELED_EVENT_TYPE))
                .isZero();
        // 자동 처리가 이겼으면 캠프 리더의 확정이 거부되고, 캠프 리더가 이겼으면 자동 처리가 false를 돌려준다. 둘 중 하나만 이긴다.
        int leaderWins = failures.isEmpty() ? 1 : 0;
        assertThat(leaderWins + autoProcessed.get()).isEqualTo(1);
        assertThat(failures).allSatisfy(failure -> assertInvalidState(failure));
    }

    @RepeatedTest(5)
    @DisplayName("[F-14][NFR-10] 자동 처리가 동시에 두 번 돌아도 한 번만 전이하고 이벤트도 하나다")
    void twoAutoProcessesTransitionOnce() throws Exception {
        long basecampId = fixture.saveBasecampWithMembers("RECRUITING", 1);
        AtomicInteger processed = new AtomicInteger();

        List<Throwable> failures = runConcurrently(List.of(
                () -> countIfTrue(applier.processDayBeforeDeparture(basecampId, AUTO_NOW), processed),
                () -> countIfTrue(applier.processDayBeforeDeparture(basecampId, AUTO_NOW), processed)));

        assertThat(failures).isEmpty();
        assertThat(processed.get()).isEqualTo(1);
        assertThat(fixture.statusOf(basecampId)).isEqualTo("CONFIRMED");
        assertThat(fixture.totalEventCount()).isEqualTo(1);
    }

    @RepeatedTest(5)
    @DisplayName("[F-14][NFR-10] 완료 처리가 동시에 두 번 돌아도 한 번만 완료하고 이벤트도 하나다")
    void twoCompletionsTransitionOnce() throws Exception {
        long basecampId = fixture.saveBasecampWithMembers("CONFIRMED", 1);
        AtomicInteger processed = new AtomicInteger();

        List<Throwable> failures = runConcurrently(List.of(
                () -> countIfTrue(applier.complete(basecampId, AFTER_END, AUTO_NOW), processed),
                () -> countIfTrue(applier.complete(basecampId, AFTER_END, AUTO_NOW), processed)));

        assertThat(failures).isEmpty();
        assertThat(processed.get()).isEqualTo(1);
        assertThat(fixture.statusOf(basecampId)).isEqualTo("COMPLETED");
        assertThat(fixture.eventCount(BasecampTransitionEvents.COMPLETED_EVENT_TYPE, basecampId))
                .isEqualTo(1);
    }

    @RepeatedTest(5)
    @DisplayName("[F-14][NFR-10] 캠프 리더의 취소와 자동 처리가 동시에 오면 최종 상태는 항상 취소이고, 자동으로 확정한 경우에만 확정 이벤트가 하나 더 남는다")
    void leaderCancelAndAutoProcessEndInCanceled() throws Exception {
        long basecampId = fixture.saveBasecampWithMembers("RECRUITING", 1);
        long leaderId = fixture.leaderIdOf(basecampId);
        AtomicInteger autoProcessed = new AtomicInteger();

        List<Throwable> failures = runConcurrently(List.of(
                () -> transitionService.cancel(basecampId, leaderId),
                () -> countIfTrue(applier.processDayBeforeDeparture(basecampId, AUTO_NOW), autoProcessed)));

        // 확정된 베이스캠프도 캠프 리더가 취소할 수 있으므로, 어느 순서로 실행되든 취소는 성공한다.
        assertThat(failures).isEmpty();
        assertThat(jdbc.queryForMap("SELECT status, cancel_reason FROM basecamp WHERE id = ?", basecampId))
                .containsEntry("status", "CANCELED")
                .containsEntry("cancel_reason", "LEADER");
        assertThat(fixture.eventCount(BasecampTransitionEvents.CANCELED_EVENT_TYPE, basecampId))
                .isEqualTo(1);
        assertThat(fixture.eventCount(BasecampTransitionEvents.CONFIRMED_EVENT_TYPE, basecampId))
                .isEqualTo(autoProcessed.get());
        assertThat(fixture.totalEventCount()).isEqualTo(1 + autoProcessed.get());
    }

    @RepeatedTest(5)
    @DisplayName("[F-14][NFR-10] 멤버의 탈퇴와 자동 처리가 동시에 오면 확정 이벤트에는 확정 시점의 인원 2명 이상이, 취소 이벤트에는 캠프 리더만 담긴다")
    void memberLeaveAndAutoProcessSeeConsistentHeadcount() throws Exception {
        long basecampId = fixture.saveBasecampWithMembers("RECRUITING", 1);
        long leaderId = fixture.leaderIdOf(basecampId);
        long memberId = fixture.firstMemberIdOf(basecampId);
        AtomicInteger autoProcessed = new AtomicInteger();

        List<Throwable> failures = runConcurrently(List.of(
                () -> {
                    membershipService.leave(basecampId, memberId);
                    return null;
                },
                () -> countIfTrue(applier.processDayBeforeDeparture(basecampId, AUTO_NOW), autoProcessed)));

        // 확정된 뒤의 탈퇴도 허용되므로 탈퇴는 어느 순서에서든 성공하고, 자동 처리는 정확히 한 번 전이한다.
        assertThat(failures).isEmpty();
        assertThat(autoProcessed.get()).isEqualTo(1);
        assertThat(fixture.activeMemberCount(basecampId)).isEqualTo(1);
        assertThat(fixture.eventCount(BasecampTransitionEvents.CONFIRMED_EVENT_TYPE, basecampId)
                        + fixture.eventCount(BasecampTransitionEvents.CANCELED_EVENT_TYPE, basecampId))
                .isEqualTo(1);
        if (fixture.statusOf(basecampId).equals("CANCELED")) {
            // 탈퇴가 먼저 커밋된 경우다. 자동 처리가 탈퇴를 보지 못했다면 확정했을 것이다.
            assertThat(jdbc.queryForObject("SELECT cancel_reason FROM basecamp WHERE id = ?", String.class, basecampId))
                    .isEqualTo("NOT_ENOUGH_MEMBERS");
            assertThat(fixture.payloadMemberIdsOf(BasecampTransitionEvents.CANCELED_EVENT_TYPE, basecampId))
                    .containsExactly(leaderId);
            return;
        }
        // 자동 처리가 먼저 확정한 경우다. 확정 이벤트는 확정한 시점의 멤버 둘을 모두 담는다.
        assertThat(fixture.statusOf(basecampId)).isEqualTo("CONFIRMED");
        assertThat(fixture.payloadMemberIdsOf(BasecampTransitionEvents.CONFIRMED_EVENT_TYPE, basecampId))
                .containsExactly(leaderId, memberId);
    }

    private static Object countIfTrue(boolean processed, AtomicInteger counter) {
        if (processed) {
            counter.incrementAndGet();
        }
        return processed;
    }

    private static void assertInvalidState(Throwable failure) {
        assertThat(failure)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(BasecampErrorCode.BASECAMP_INVALID_STATE));
    }

    // 작업마다 스레드 하나로 동시에 출발시키고, 던져진 예외를 모아 돌려준다.
    private List<Throwable> runConcurrently(List<Callable<Object>> actions) throws Exception {
        int threads = actions.size();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> action : actions) {
                Callable<Object> task = () -> {
                    ready.countDown();
                    start.await();
                    return action.call();
                };
                futures.add(executor.submit(task));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return collectFailures(futures);
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private List<Throwable> collectFailures(List<Future<Object>> futures) throws InterruptedException {
        List<Throwable> failures = new ArrayList<>();
        for (Future<Object> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                failures.add(e.getCause());
            } catch (TimeoutException e) {
                failures.add(e);
            }
        }
        return failures;
    }
}
