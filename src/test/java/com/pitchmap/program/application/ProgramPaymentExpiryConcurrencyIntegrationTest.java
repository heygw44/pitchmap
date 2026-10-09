package com.pitchmap.program.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.program.domain.ProgramErrorCode;
import com.pitchmap.program.infra.ExpiryTarget;
import java.time.Duration;
import java.time.Instant;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ProgramPaymentExpiryConcurrencyIntegrationTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;
    private static final String EXPIRED_EVENT = "PROGRAM_APPLICATION_EXPIRED";
    private static final String CONFIRMED_EVENT = "PROGRAM_APPLICATION_CONFIRMED";
    private static final int APPLICATIONS = 30;

    @Autowired
    private ProgramPaymentService programPaymentService;

    @Autowired
    private ProgramPaymentExpiryApplier applier;

    @Autowired
    private ProgramPaymentExpiryService expiryService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    private ProgramApplyFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ProgramApplyFixture(jdbc, memberRepository);
    }

    @RepeatedTest(10)
    @DisplayName("[F-19][PG-05] 기한 직전의 결제와 기한이 지난 뒤의 만료가 동시에 일어나면 (확정, 결제 1건) 또는 (만료, 결제 0건) 중 정확히 하나로 끝난다")
    void payAndExpireEndWithExactlyOneOutcome() throws Exception {
        // given
        long programId = fixture.saveProgram(3, false);
        long memberId = fixture.saveMember();
        Instant dueAt = NOW.plus(Duration.ofMinutes(1));
        long applicationId = fixture.saveApplication(programId, memberId, "PENDING_PAYMENT", dueAt);
        ExpiryTarget target = new ExpiryTarget(applicationId, memberId, programId);
        // 결제는 기한 1초 전 시각에 읽고, 만료는 기한 정각을 넘겨 호출한다. 두 작업이 같은 신청 행을 두고 경쟁한다.
        clock.setInstant(dueAt.minusSeconds(1));

        // when
        Outcomes outcomes = runConcurrently(
                List.of(() -> programPaymentService.pay(memberId, applicationId), () -> applier.expire(target, dueAt)));

        // then
        boolean expired = fixture.applicationStatus(applicationId).equals("EXPIRED");
        if (expired) {
            assertThat(outcomes.failures())
                    .hasSize(1)
                    .allSatisfy(failure -> assertThat(failure)
                            .isInstanceOfSatisfying(
                                    BusinessException.class,
                                    e -> assertThat(e.getErrorCode())
                                            .isEqualTo(ProgramErrorCode.PROGRAM_PAYMENT_EXPIRED)));
            assertThat(outcomes.successes()).containsExactly(true);
            assertThat(fixture.paymentCount(applicationId)).isZero();
            assertThat(fixture.outboxCount(EXPIRED_EVENT, applicationId)).isEqualTo(1);
            assertThat(fixture.outboxCount(CONFIRMED_EVENT, applicationId)).isZero();
        } else {
            assertThat(fixture.applicationStatus(applicationId)).isEqualTo("CONFIRMED");
            assertThat(outcomes.failures()).isEmpty();
            assertThat(outcomes.successes()).filteredOn(Boolean.FALSE::equals).hasSize(1);
            assertThat(fixture.paymentCount(applicationId)).isEqualTo(1);
            assertThat(fixture.outboxCount(CONFIRMED_EVENT, applicationId)).isEqualTo(1);
            assertThat(fixture.outboxCount(EXPIRED_EVENT, applicationId)).isZero();
        }
    }

    @Test
    @DisplayName("[F-19][PG-05] 만료 작업 두 곳이 동시에 돌아도 모든 신청이 한 번씩만 만료되고 이벤트도 신청마다 1건이다")
    void twoExpiryRunsExpireEachApplicationOnce() throws Exception {
        // given
        long programId = fixture.saveProgram(APPLICATIONS, false);
        List<Long> applicationIds = new ArrayList<>();
        for (int i = 0; i < APPLICATIONS; i++) {
            applicationIds.add(fixture.saveApplication(
                    programId, fixture.saveMember(), "PENDING_PAYMENT", NOW.plus(Duration.ofMinutes(15))));
        }
        clock.advance(Duration.ofMinutes(16));

        // when
        Outcomes outcomes = runConcurrently(List.of(
                () -> expiryService.run().expired(), () -> expiryService.run().expired()));

        // then
        assertThat(outcomes.failures()).isEmpty();
        assertThat(outcomes.successes().stream().mapToInt(Integer.class::cast).sum())
                .isEqualTo(APPLICATIONS);
        assertThat(fixture.activeCount(programId)).isZero();
        for (long applicationId : applicationIds) {
            assertThat(fixture.applicationStatus(applicationId)).isEqualTo("EXPIRED");
            assertThat(fixture.outboxCount(EXPIRED_EVENT, applicationId)).isEqualTo(1);
        }
    }

    private record Outcomes(List<Object> successes, List<Throwable> failures) {}

    // 작업마다 스레드 하나로 동시에 출발시키고, 반환값과 던져진 예외를 모아 돌려준다.
    private Outcomes runConcurrently(List<Callable<Object>> actions) throws Exception {
        int threads = actions.size();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> action : actions) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return action.call();
                }));
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
