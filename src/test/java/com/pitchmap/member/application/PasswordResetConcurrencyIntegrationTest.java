package com.pitchmap.member.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.domain.MemberBuilder;
import com.pitchmap.member.domain.MemberErrorCode;
import com.pitchmap.member.domain.MemberException;
import com.pitchmap.member.domain.PasswordResetToken;
import com.pitchmap.member.domain.PasswordResetTokenRepository;
import com.pitchmap.member.domain.ResetToken;
import com.pitchmap.member.infra.MemberJpaRepository;
import java.security.SecureRandom;
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
import org.springframework.security.crypto.password.PasswordEncoder;

@IntegrationTest
class PasswordResetConcurrencyIntegrationTest {

    private static final int THREADS = 10;
    private static final String OLD_PASSWORD = "Valid-pass1";

    @Autowired
    private PasswordResetConfirmService confirmService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @RepeatedTest(5)
    @DisplayName("[PW-02] 같은 토큰으로 서로 다른 새 비밀번호를 동시에 10번 확인하면 1건만 성공하고 나머지는 PASSWORD_RESET_TOKEN_INVALID")
    void onlyOneConfirmSucceedsForConcurrentUse() throws Exception {
        // given
        Member member = memberRepository.saveAndFlush(MemberBuilder.aMember()
                .passwordHash(passwordEncoder.encode(OLD_PASSWORD))
                .now(clock.instant())
                .build());
        String rawToken = ResetToken.generate(new SecureRandom()).value();
        tokenRepository.save(PasswordResetToken.issue(member.getId(), ResetToken.hash(rawToken), clock.instant()));
        List<String> candidates = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            candidates.add("Newer-pass" + i);
        }

        // when
        List<Outcome> outcomes = runConcurrently(rawToken, candidates);

        // then
        List<Outcome> winners = outcomes.stream().filter(Outcome::succeeded).toList();
        assertThat(winners).hasSize(1);
        assertThat(outcomes.stream().filter(outcome -> !outcome.succeeded()))
                .hasSize(THREADS - 1)
                .allSatisfy(outcome -> assertThat(outcome.failure())
                        .isInstanceOfSatisfying(
                                MemberException.class,
                                e -> assertThat(e.getErrorCode())
                                        .isEqualTo(MemberErrorCode.PASSWORD_RESET_TOKEN_INVALID)));
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM password_reset_token WHERE member_id = ? AND used_at IS NOT NULL",
                        Long.class,
                        member.getId()))
                .isEqualTo(1L);
        String storedHash =
                jdbc.queryForObject("SELECT password_hash FROM member WHERE id = ?", String.class, member.getId());
        List<String> matching = candidates.stream()
                .filter(candidate -> passwordEncoder.matches(candidate, storedHash))
                .toList();
        assertThat(matching).containsExactly(winners.get(0).password());
        assertThat(passwordEncoder.matches(OLD_PASSWORD, storedHash)).isFalse();
    }

    // 스레드마다 별도 트랜잭션으로 호출한다. 시작 신호를 모두가 기다렸다가 동시에 출발한다.
    private List<Outcome> runConcurrently(String rawToken, List<String> passwords) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (String password : passwords) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        confirmService.confirm(rawToken, password);
                        return new Outcome(password, null);
                    } catch (RuntimeException e) {
                        return new Outcome(password, e);
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(get(future));
            }
            return outcomes;
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static Outcome get(Future<Outcome> future) throws Exception {
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw new AssertionError("스레드가 예기치 않게 끝났습니다.", e.getCause());
        }
    }

    // 성공이면 failure가 null이다. 실패는 던진 예외를 그대로 담아 MemberException이 아닌 예외도 걸러낸다.
    private record Outcome(String password, RuntimeException failure) {

        boolean succeeded() {
            return failure == null;
        }

        // 레코드 기본 toString은 비밀번호를 찍는다. 단언 실패 메시지에 새지 않게 숨긴다.
        @Override
        public String toString() {
            return "Outcome[" + (succeeded() ? "success" : failure.getClass().getSimpleName()) + "]";
        }
    }
}
