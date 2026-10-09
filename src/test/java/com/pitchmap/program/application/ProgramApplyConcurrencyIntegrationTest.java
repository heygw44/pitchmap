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
import java.util.Collections;
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
class ProgramApplyConcurrencyIntegrationTest {

    private static final int CAPACITY = 5;
    private static final int APPLICANTS = CAPACITY * 4;
    private static final int SAME_MEMBER_THREADS = 6;

    @Autowired
    private ProgramApplyService programApplyService;

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
    @DisplayName("[F-18][NFR-02] 정원 5에 서로 다른 회원 20명이 동시에 신청하면 5건만 성공하고 나머지는 PROGRAM_SOLD_OUT이며 결제 대기·확정 신청은 5건이다")
    void successCountEqualsCapacityUnderConcurrentApplications() throws Exception {
        // given
        long programId = fixture.saveProgram(CAPACITY, false);
        List<Long> memberIds = new ArrayList<>();
        for (int i = 0; i < APPLICANTS; i++) {
            memberIds.add(fixture.saveMember());
        }

        // when
        Outcomes outcomes = runConcurrently(memberIds, memberId -> programApplyService.apply(memberId, programId));

        // then
        assertThat(outcomes.successes()).hasSize(CAPACITY);
        assertThat(outcomes.failures())
                .hasSize(APPLICANTS - CAPACITY)
                .allSatisfy(failure -> assertErrorCode(failure, ProgramErrorCode.PROGRAM_SOLD_OUT));
        assertThat(fixture.activeCount(programId)).isEqualTo(CAPACITY);
    }

    @RepeatedTest(5)
    @DisplayName("[F-18][NFR-03] 같은 회원이 같은 멱등성 키로 동시에 여러 번 신청하면 신청은 1건이고 나머지는 같은 결과를 받거나 IDEMPOTENCY_IN_PROGRESS이다")
    void sameKeyCreatesOneApplication() throws Exception {
        // given
        long programId = fixture.saveProgram(CAPACITY, false);
        long memberId = fixture.saveMember();
        List<Long> sameMember = Collections.nCopies(SAME_MEMBER_THREADS, memberId);

        // when
        Outcomes outcomes = runConcurrently(sameMember, id -> applyWithKey(id, programId, "same-key"));

        // then
        assertThat(outcomes.successes()).isNotEmpty();
        assertThat(outcomes.successes().stream().distinct()).hasSize(1);
        assertThat(outcomes.failures())
                .allSatisfy(failure -> assertErrorCode(failure, CommonErrorCode.IDEMPOTENCY_IN_PROGRESS));
        assertThat(fixture.applicationCount(programId, memberId)).isEqualTo(1);
        assertThat(fixture.activeCount(programId)).isEqualTo(1);
    }

    @RepeatedTest(5)
    @DisplayName("[F-18][PG-03] 같은 회원이 다른 멱등성 키로 동시에 여러 번 신청하면 신청은 1건이고 나머지는 PROGRAM_ALREADY_APPLIED이다")
    void differentKeysCreateOneApplication() throws Exception {
        // given
        long programId = fixture.saveProgram(CAPACITY, false);
        long memberId = fixture.saveMember();
        List<Long> sameMember = Collections.nCopies(SAME_MEMBER_THREADS, memberId);
        AtomicInteger sequence = new AtomicInteger();

        // when
        Outcomes outcomes =
                runConcurrently(sameMember, id -> applyWithKey(id, programId, "key-" + sequence.incrementAndGet()));

        // then
        assertThat(outcomes.successes()).hasSize(1);
        assertThat(outcomes.failures())
                .hasSize(SAME_MEMBER_THREADS - 1)
                .allSatisfy(failure -> assertErrorCode(failure, ProgramErrorCode.PROGRAM_ALREADY_APPLIED));
        assertThat(fixture.applicationCount(programId, memberId)).isEqualTo(1);
        assertThat(fixture.activeCount(programId)).isEqualTo(1);
    }

    // 컨트롤러와 같은 방식으로 멱등성 실행기를 거쳐 신청한다. 결과는 신청 ID로 비교한다.
    private Long applyWithKey(long memberId, long programId, String key) {
        ProgramApplyResult result = idempotencyExecutor.execute(
                new IdempotentRequest(memberId, key, "POST /api/programs/" + programId + "/applications", null),
                HttpStatus.CREATED,
                ProgramApplyResult.class,
                () -> programApplyService.apply(memberId, programId));
        return result.applicationId();
    }

    private static void assertErrorCode(Throwable failure, ErrorCode expected) {
        assertThat(failure)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    private record Outcomes(List<Object> successes, List<Throwable> failures) {}

    // 회원마다 스레드 하나로 동시에 출발시키고, 반환값과 던져진 예외를 모아 돌려준다.
    private Outcomes runConcurrently(List<Long> memberIds, MemberAction action) throws Exception {
        int threads = memberIds.size();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (long memberId : memberIds) {
                Callable<Object> task = () -> {
                    ready.countDown();
                    start.await();
                    return action.run(memberId);
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

    @FunctionalInterface
    private interface MemberAction {
        Object run(long memberId) throws Exception;
    }
}
