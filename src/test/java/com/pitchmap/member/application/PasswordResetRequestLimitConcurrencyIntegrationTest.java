package com.pitchmap.member.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.error.RateLimitedException;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.domain.MemberBuilder;
import com.pitchmap.member.domain.MemberErrorCode;
import com.pitchmap.member.domain.PasswordResetRequestPolicy;
import com.pitchmap.member.infra.MemberJpaRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class PasswordResetRequestLimitConcurrencyIntegrationTest {

    private static final int SAME_EMAIL_THREADS = 20;
    private static final int SAME_IP_THREADS = 30;
    private static final String SHARED_IP = "203.0.113.7";

    @Autowired
    private PasswordResetRequestService requestService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @RepeatedTest(5)
    @DisplayName("[F-02][PW-05] 같은 가입 이메일로 서로 다른 IP 20개가 동시에 요청하면 1건만 통과하고 나머지는 PASSWORD_RESET_LIMITED다")
    void sameRegisteredEmailFromManyIpsPassesOnce() throws Exception {
        // given
        Member member = memberRepository.saveAndFlush(
                MemberBuilder.aMember().now(clock.instant()).build());
        List<Request> requests = new ArrayList<>();
        for (int i = 0; i < SAME_EMAIL_THREADS; i++) {
            requests.add(new Request(member.getEmail(), "198.51.100." + (i + 1)));
        }

        // when
        List<Throwable> failures = runConcurrently(requests);

        // then
        assertOnlyLimitedFailures(failures);
        assertThat(failures.stream().filter(failure -> failure == null)).hasSize(1);
        assertThat(failures.stream().filter(failure -> failure != null)).hasSize(SAME_EMAIL_THREADS - 1);
        assertThat(countRows("outbox_event")).isEqualTo(1);
        assertThat(requestCountOf(PasswordResetRequestPolicy.emailKey(member.getEmail())))
                .isEqualTo(1);
        assertThat(countRows("password_reset_throttle")).isEqualTo(2);
    }

    @RepeatedTest(5)
    @DisplayName("[F-02][PW-05] 같은 이메일이 가입되지 않은 주소여도 통과 1건, 한도 19건으로 가입 여부와 무관하다")
    void sameUnknownEmailFromManyIpsPassesOnce() throws Exception {
        // given
        String email = TestSequence.email();
        List<Request> requests = new ArrayList<>();
        for (int i = 0; i < SAME_EMAIL_THREADS; i++) {
            requests.add(new Request(email, "198.51.100." + (i + 1)));
        }

        // when
        List<Throwable> failures = runConcurrently(requests);

        // then
        assertOnlyLimitedFailures(failures);
        assertThat(failures.stream().filter(failure -> failure == null)).hasSize(1);
        assertThat(failures.stream().filter(failure -> failure != null)).hasSize(SAME_EMAIL_THREADS - 1);
        assertThat(countRows("outbox_event")).isZero();
        assertThat(requestCountOf(PasswordResetRequestPolicy.emailKey(email))).isEqualTo(1);
    }

    @RepeatedTest(5)
    @DisplayName("[F-02][PW-05] 같은 IP로 서로 다른 이메일 30개가 동시에 요청하면 정확히 20건이 통과하고 10건은 한도에 걸리며 IP 횟수는 20이다")
    void sameIpWithManyEmailsPassesTwenty() throws Exception {
        // given
        List<Request> requests = new ArrayList<>();
        for (int i = 0; i < SAME_IP_THREADS; i++) {
            requests.add(new Request(TestSequence.email(), SHARED_IP));
        }

        // when
        List<Throwable> failures = runConcurrently(requests);

        // then
        assertOnlyLimitedFailures(failures);
        assertThat(failures.stream().filter(failure -> failure == null)).hasSize(20);
        assertThat(failures.stream().filter(failure -> failure != null)).hasSize(SAME_IP_THREADS - 20);
        assertThat(requestCountOf(PasswordResetRequestPolicy.ipKey(SHARED_IP))).isEqualTo(20);
        assertThat(countRows("password_reset_throttle")).isEqualTo(21);
    }

    // 실패는 모두 한도 예외여야 한다. 교착이나 잠금 시간 초과, 커넥션 풀 고갈은 다른 예외로 드러난다.
    private static void assertOnlyLimitedFailures(List<Throwable> failures) {
        assertThat(failures)
                .filteredOn(failure -> failure != null)
                .allSatisfy(failure -> assertThat(failure)
                        .isInstanceOfSatisfying(
                                RateLimitedException.class,
                                e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.PASSWORD_RESET_LIMITED)));
    }

    // 요청마다 스레드를 따로 쓰고, 모두 준비된 뒤에 시작 신호를 한꺼번에 준다. 성공이면 null, 실패면 던진 예외를 담는다.
    private List<Throwable> runConcurrently(List<Request> requests) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(requests.size());
        CountDownLatch ready = new CountDownLatch(requests.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Throwable>> futures = new ArrayList<>();
            for (Request request : requests) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        requestService.request(request.email(), request.ip());
                        return null;
                    } catch (RuntimeException e) {
                        return e;
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Throwable> failures = new ArrayList<>();
            for (Future<Throwable> future : futures) {
                failures.add(get(future));
            }
            return failures;
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static Throwable get(Future<Throwable> future) throws Exception {
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw new AssertionError("스레드가 예기치 않게 끝났습니다.", e.getCause());
        }
    }

    private Integer requestCountOf(String key) {
        List<Integer> counts = jdbc.queryForList(
                "SELECT request_count FROM password_reset_throttle WHERE throttle_key = ?", Integer.class, key);
        return counts.isEmpty() ? null : counts.get(0);
    }

    private int countRows(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private record Request(String email, String ip) {}
}
