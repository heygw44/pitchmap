package com.pitchmap.member.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.domain.MemberErrorCode;
import com.pitchmap.member.domain.MemberException;
import com.pitchmap.member.infra.MemberJpaRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class MemberSignupConcurrencyIntegrationTest {

    private static final int THREADS = 10;
    private static final String VALID_PASSWORD = "Passw0rd!xyz";

    @Autowired
    MemberSignupService memberSignupService;

    @Autowired
    MemberJpaRepository memberJpaRepository;

    @RepeatedTest(5)
    @DisplayName("[F-01][EV-06] 같은 이메일로 동시에 가입하면 1건만 성공하고 나머지는 MEMBER_EMAIL_DUPLICATED")
    void onlyOneSignupSucceedsForSameEmail() throws Exception {
        // given
        String email = TestSequence.email();

        // when
        List<Throwable> failures =
                signUpConcurrently(i -> new SignupCommand(email, VALID_PASSWORD, TestSequence.nickname()));

        // then
        assertOnlyDuplicateFailures(failures, MemberErrorCode.MEMBER_EMAIL_DUPLICATED);
        assertThat(memberJpaRepository.count()).isEqualTo(1);
        assertThat(memberJpaRepository.existsByEmail(email)).isTrue();
    }

    @RepeatedTest(5)
    @DisplayName("[F-01] 같은 닉네임으로 동시에 가입하면 1건만 성공하고 나머지는 MEMBER_NICKNAME_DUPLICATED")
    void onlyOneSignupSucceedsForSameNickname() throws Exception {
        // given
        String nickname = TestSequence.nickname();

        // when
        List<Throwable> failures =
                signUpConcurrently(i -> new SignupCommand(TestSequence.email(), VALID_PASSWORD, nickname));

        // then
        assertOnlyDuplicateFailures(failures, MemberErrorCode.MEMBER_NICKNAME_DUPLICATED);
        assertThat(memberJpaRepository.count()).isEqualTo(1);
        assertThat(memberJpaRepository.existsByNickname(nickname)).isTrue();
    }

    private void assertOnlyDuplicateFailures(List<Throwable> failures, MemberErrorCode expected) {
        assertThat(failures)
                .hasSize(THREADS - 1)
                .allSatisfy(failure -> assertThat(failure)
                        .isInstanceOfSatisfying(
                                MemberException.class,
                                e -> assertThat(e.getErrorCode()).isEqualTo(expected)));
    }

    private List<Throwable> signUpConcurrently(IntFunction<SignupCommand> commandFactory) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<SignupResult>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                SignupCommand command = commandFactory.apply(i);
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return memberSignupService.signUp(command);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return collectFailures(futures);
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private List<Throwable> collectFailures(List<Future<SignupResult>> futures) throws InterruptedException {
        List<Throwable> failures = new ArrayList<>();
        for (Future<SignupResult> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                failures.add(e.getCause());
            } catch (java.util.concurrent.TimeoutException e) {
                failures.add(e);
            }
        }
        return failures;
    }
}
