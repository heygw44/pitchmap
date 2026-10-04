package com.pitchmap.member.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.domain.MemberBuilder;
import com.pitchmap.member.domain.MemberErrorCode;
import com.pitchmap.member.infra.MemberJpaRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class EmailVerificationConcurrencyIntegrationTest {

    private static final int THREADS = 10;
    private static final int MAX_ATTEMPTS = 5;
    private static final String IP = "203.0.113.7";
    private static final String EVENT_TYPE = "EMAIL_VERIFICATION_REQUESTED";
    private static final String SUCCESS = "SUCCESS";

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @RepeatedTest(5)
    @DisplayName("[F-01][EV-03] 같은 회원이 재발송을 동시에 10번 요청하면 이벤트는 1건만 기록되고 나머지는 EMAIL_RESEND_LIMITED")
    void onlyOneResendEventIsRecordedForConcurrentRequests() throws Exception {
        // given
        Member member = newMember();

        // when
        List<String> outcomes = runConcurrently(() -> {
            emailVerificationService.requestResend(member.getId(), IP);
            return null;
        });

        // then
        assertThat(countByOutcome(outcomes))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(SUCCESS, 1L, MemberErrorCode.EMAIL_RESEND_LIMITED.name(), (long) THREADS - 1));
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM outbox_event WHERE event_type = ? AND aggregate_id = ?",
                        Long.class,
                        EVENT_TYPE,
                        member.getId()))
                .isEqualTo(1L);
    }

    @RepeatedTest(5)
    @DisplayName("[F-01][EV-02] 맞는 코드를 동시에 10번 입력하면 1건만 성공하고 나머지는 EMAIL_ALREADY_VERIFIED")
    void onlyOneVerificationSucceedsForConcurrentCorrectCodes() throws Exception {
        // given
        Member member = newMember();
        String code = issueCode(member);

        // when
        List<String> outcomes = runConcurrently(() -> emailVerificationService.verify(member.getId(), code));

        // then
        assertThat(countByOutcome(outcomes))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(SUCCESS, 1L, MemberErrorCode.EMAIL_ALREADY_VERIFIED.name(), (long) THREADS - 1));
        assertThat(jdbc.queryForObject("SELECT status FROM member WHERE id = ?", String.class, member.getId()))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM email_verification WHERE member_id = ? AND verified_at IS NOT NULL",
                        Long.class,
                        member.getId()))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                        "SELECT attempt_count FROM email_verification WHERE member_id = ?",
                        Integer.class,
                        member.getId()))
                .isZero();
    }

    @RepeatedTest(5)
    @DisplayName(
            "[F-01][EV-02] 틀린 코드를 동시에 10번 입력해도 시도 횟수는 정확히 5이고 5건은 EMAIL_CODE_INVALID, 5건은 EMAIL_CODE_ATTEMPTS_EXCEEDED")
    void attemptCountStopsExactlyAtLimitForConcurrentWrongCodes() throws Exception {
        // given
        Member member = newMember();
        String code = issueCode(member);
        String wrongCode = code.equals("000000") ? "999999" : "000000";

        // when
        List<String> outcomes = runConcurrently(() -> emailVerificationService.verify(member.getId(), wrongCode));

        // then
        assertThat(countByOutcome(outcomes))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        MemberErrorCode.EMAIL_CODE_INVALID.name(),
                        (long) MAX_ATTEMPTS,
                        MemberErrorCode.EMAIL_CODE_ATTEMPTS_EXCEEDED.name(),
                        (long) (THREADS - MAX_ATTEMPTS)));
        assertThat(jdbc.queryForObject(
                        "SELECT attempt_count FROM email_verification WHERE member_id = ?",
                        Integer.class,
                        member.getId()))
                .isEqualTo(MAX_ATTEMPTS);
        assertThat(jdbc.queryForObject("SELECT status FROM member WHERE id = ?", String.class, member.getId()))
                .isEqualTo("UNVERIFIED");
    }

    private Member newMember() {
        return memberRepository.saveAndFlush(
                MemberBuilder.aMember().now(clock.instant()).build());
    }

    private String issueCode(Member member) {
        return emailVerificationService
                .issueFor(member.getId(), IP)
                .orElseThrow(() -> new AssertionError("미인증 회원인데 코드를 발급하지 않았습니다."))
                .code();
    }

    private static Map<String, Long> countByOutcome(List<String> outcomes) {
        return outcomes.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    // 스레드마다 별도 트랜잭션으로 호출한다. 성공이면 SUCCESS, 도메인 예외면 오류 코드 이름, 그 밖의 예외는 예외 종류를 돌려준다.
    private List<String> runConcurrently(Callable<?> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return collectOutcomes(futures);
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private List<String> collectOutcomes(List<Future<?>> futures) throws InterruptedException {
        List<String> outcomes = new ArrayList<>();
        for (Future<?> future : futures) {
            outcomes.add(outcomeOf(future));
        }
        return outcomes;
    }

    private String outcomeOf(Future<?> future) throws InterruptedException {
        try {
            future.get(30, TimeUnit.SECONDS);
            return SUCCESS;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof BusinessException businessException) {
                return businessException.getErrorCode().name();
            }
            return e.getCause().getClass().getName();
        } catch (TimeoutException e) {
            return "TIMEOUT";
        }
    }
}
