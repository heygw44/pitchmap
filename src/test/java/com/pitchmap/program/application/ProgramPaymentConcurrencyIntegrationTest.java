package com.pitchmap.program.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.error.ErrorCode;
import com.pitchmap.common.idempotency.IdempotencyExecutor;
import com.pitchmap.common.idempotency.IdempotentRequest;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.program.domain.ProgramErrorCode;
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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ProgramPaymentConcurrencyIntegrationTest {

    private static final int THREADS = 6;

    @Autowired
    private ProgramPaymentService programPaymentService;

    @Autowired
    private ProgramAdminService programAdminService;

    @Autowired
    private IdempotencyExecutor idempotencyExecutor;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private ProgramApplyFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ProgramApplyFixture(jdbc, memberRepository);
    }

    @RepeatedTest(5)
    @DisplayName("[F-19][NFR-03] 같은 신청을 다른 멱등성 키로 동시에 결제하면 한 번만 성공하고 나머지는 PROGRAM_INVALID_STATE이며 결제 행은 1건이다")
    void differentKeysPayOnce() throws Exception {
        // given
        long programId = fixture.saveProgram(3, false);
        long memberId = fixture.saveMember();
        long applicationId = fixture.saveApplication(programId, memberId, "PENDING_PAYMENT");
        AtomicInteger sequence = new AtomicInteger();

        // when
        Outcomes outcomes = runConcurrently(
                THREADS, () -> payWithKey(memberId, applicationId, "key-" + sequence.incrementAndGet()));

        // then
        assertThat(outcomes.successes()).hasSize(1);
        assertThat(outcomes.failures())
                .hasSize(THREADS - 1)
                .allSatisfy(failure -> assertErrorCode(failure, ProgramErrorCode.PROGRAM_INVALID_STATE));
        assertThat(fixture.paymentCount(applicationId)).isEqualTo(1);
        assertThat(fixture.applicationStatus(applicationId)).isEqualTo("CONFIRMED");
        assertThat(fixture.outboxCount("PROGRAM_APPLICATION_CONFIRMED", applicationId))
                .isEqualTo(1);
    }

    @RepeatedTest(5)
    @DisplayName("[F-19][NFR-03] 같은 신청을 같은 멱등성 키로 동시에 결제하면 결제 행은 1건이고 나머지는 같은 결과를 받거나 IDEMPOTENCY_IN_PROGRESS이다")
    void sameKeyPaysOnce() throws Exception {
        // given
        long programId = fixture.saveProgram(3, false);
        long memberId = fixture.saveMember();
        long applicationId = fixture.saveApplication(programId, memberId, "PENDING_PAYMENT");

        // when
        Outcomes outcomes = runConcurrently(THREADS, () -> payWithKey(memberId, applicationId, "same-key"));

        // then
        assertThat(outcomes.successes()).isNotEmpty();
        assertThat(outcomes.successes().stream().distinct()).hasSize(1);
        assertThat(outcomes.failures())
                .allSatisfy(failure -> assertErrorCode(failure, CommonErrorCode.IDEMPOTENCY_IN_PROGRESS));
        assertThat(fixture.paymentCount(applicationId)).isEqualTo(1);
        assertThat(fixture.outboxCount("PROGRAM_APPLICATION_CONFIRMED", applicationId))
                .isEqualTo(1);
    }

    @RepeatedTest(5)
    @DisplayName("[F-19] 결제와 관리자 행사 취소가 동시에 일어나면 (결제 후 취소·환불) 또는 (결제 없이 취소) 중 하나로 끝나고 환불 알림 이벤트가 결과와 같다")
    void payAndProgramCancelEndConsistently() throws Exception {
        // given
        long programId = fixture.saveProgram(3, false);
        long memberId = fixture.saveMember();
        long adminId = fixture.saveMember();
        long applicationId = fixture.saveApplication(programId, memberId, "PENDING_PAYMENT");

        // when
        List<Callable<Object>> actions = List.of(
                () -> programPaymentService.pay(memberId, applicationId),
                () -> programAdminService.cancel(adminId, programId));
        Outcomes outcomes = runConcurrently(actions);

        // then
        assertThat(fixture.applicationStatus(applicationId)).isEqualTo("CANCELED");
        assertThat(fixture.outboxCount("PROGRAM_APPLICATION_CANCELED", applicationId))
                .isEqualTo(1);
        boolean paid = fixture.paymentCount(applicationId) == 1;
        if (paid) {
            assertThat(fixture.paymentStatus(applicationId)).isEqualTo("REFUNDED");
            assertThat(outcomes.failures()).isEmpty();
            assertThat(fixture.outboxCount("PROGRAM_APPLICATION_CONFIRMED", applicationId))
                    .isEqualTo(1);
        } else {
            assertThat(outcomes.failures()).hasSize(1);
            assertThat(fixture.outboxCount("PROGRAM_APPLICATION_CONFIRMED", applicationId))
                    .isZero();
        }
        assertThat(jdbc.queryForObject(
                        "SELECT JSON_EXTRACT(payload, '$.refunded') = TRUE FROM outbox_event"
                                + " WHERE event_type = 'PROGRAM_APPLICATION_CANCELED' AND aggregate_id = ?",
                        Boolean.class,
                        applicationId))
                .isEqualTo(paid);
    }

    // 컨트롤러와 같은 방식으로 멱등성 실행기를 거쳐 결제한다. 결과는 신청 ID와 결제 시각으로 비교한다.
    private Object payWithKey(long memberId, long applicationId, String key) {
        ProgramPayResult result = idempotencyExecutor.execute(
                new IdempotentRequest(memberId, key, "POST /api/program-applications/" + applicationId + "/pay", null),
                HttpStatus.OK,
                ProgramPayResult.class,
                () -> programPaymentService.pay(memberId, applicationId));
        return result.applicationId() + "@" + result.paidAt();
    }

    private static void assertErrorCode(Throwable failure, ErrorCode expected) {
        assertThat(failure)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    private record Outcomes(List<Object> successes, List<Throwable> failures) {}

    private Outcomes runConcurrently(int threads, Callable<Object> action) throws Exception {
        List<Callable<Object>> actions = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            actions.add(action);
        }
        return runConcurrently(actions);
    }

    // 작업마다 스레드 하나로 동시에 출발시키고, 반환값과 던져진 예외를 모아 돌려준다.
    private Outcomes runConcurrently(List<Callable<Object>> actions) throws Exception {
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
            return collect(futures);
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Outcomes collect(List<Future<Object>> futures) throws InterruptedException {
        List<Object> successes = new ArrayList<>();
        List<Throwable> failures = new ArrayList<>();
        for (Future<Object> future : futures) {
            try {
                successes.add(future.get(60, TimeUnit.SECONDS));
            } catch (ExecutionException e) {
                failures.add(e.getCause());
            } catch (TimeoutException e) {
                failures.add(e);
            }
        }
        return new Outcomes(successes, failures);
    }
}
