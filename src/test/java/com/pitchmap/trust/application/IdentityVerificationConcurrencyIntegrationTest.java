package com.pitchmap.trust.application;

import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.trust.domain.Gender;
import com.pitchmap.trust.domain.TrustErrorCode;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class IdentityVerificationConcurrencyIntegrationTest {

    private static final int THREADS = 10;

    @Autowired
    private IdentityVerificationService service;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @RepeatedTest(5)
    @DisplayName("[ID-01] 같은 회원이 동시에 본인확인을 10번 요청하면 1건만 저장되고 나머지는 IDENTITY_ALREADY_VERIFIED다")
    void sameMemberIsVerifiedOnce() throws Exception {
        // given
        long memberId = saveMember();

        // when
        List<Throwable> failures = runConcurrently(
                THREADS,
                index -> service.verify(memberId, new IdentityVerifyCommand(1995, Gender.FEMALE, "demo-" + index)));

        // then
        assertFailuresAre(failures, THREADS - 1, TrustErrorCode.IDENTITY_ALREADY_VERIFIED);
        assertThat(rowCount()).isEqualTo(1);
    }

    @RepeatedTest(5)
    @DisplayName("[ID-04] 서로 다른 회원 10명이 같은 시연용 식별 문자열로 동시에 본인확인하면 1건만 저장되고 나머지는 IDENTITY_CI_DUPLICATED다")
    void sameCiIsLinkedToOneMember() throws Exception {
        // given
        List<Long> memberIds = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            memberIds.add(saveMember());
        }

        // when
        List<Throwable> failures = runConcurrently(
                THREADS,
                index -> service.verify(
                        memberIds.get(index), new IdentityVerifyCommand(1995, Gender.MALE, "same-person")));

        // then
        assertFailuresAre(failures, THREADS - 1, TrustErrorCode.IDENTITY_CI_DUPLICATED);
        assertThat(rowCount()).isEqualTo(1);
    }

    @RepeatedTest(10)
    @DisplayName(
            "[ID-04] 같은 CI의 본인확인 행을 지운 직후 서로 다른 회원 10명이 그 CI로 동시에 본인확인해도, DB 교착 상태로 실패하는 요청 없이 1건만 저장되고 나머지는 IDENTITY_CI_DUPLICATED다")
    void sameCiAfterDeletedRowIsLinkedToOneMember() throws Exception {
        // given: 지운 행은 InnoDB가 정리하기 전까지 같은 키의 삭제 표시 레코드로 인덱스에 남는다.
        // 같은 키를 동시에 넣는 트랜잭션들은 이 레코드에 공유 잠금을 건 뒤 삽입 잠금을 기다려서 교착 상태가 생길 수 있다.
        service.verify(saveMember(), new IdentityVerifyCommand(1995, Gender.MALE, "reused-person"));
        jdbc.update("DELETE FROM identity_verification");
        List<Long> memberIds = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            memberIds.add(saveMember());
        }

        // when
        List<Throwable> failures = runConcurrently(
                THREADS,
                index -> service.verify(
                        memberIds.get(index), new IdentityVerifyCommand(1995, Gender.MALE, "reused-person")));

        // then
        assertFailuresAre(failures, THREADS - 1, TrustErrorCode.IDENTITY_CI_DUPLICATED);
        assertThat(rowCount()).isEqualTo(1);
    }

    private static void assertFailuresAre(List<Throwable> failures, int expectedCount, TrustErrorCode expectedCode) {
        assertThat(failures)
                .hasSize(expectedCount)
                .allSatisfy(failure -> assertThat(failure)
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e -> assertThat(e.getErrorCode()).isEqualTo(expectedCode)));
    }

    private long saveMember() {
        return memberRepository.saveAndFlush(aMember().build()).getId();
    }

    private int rowCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM identity_verification", Integer.class);
    }

    // 요청마다 스레드 하나로 동시에 출발시키고, 던져진 예외를 모아 돌려준다.
    private List<Throwable> runConcurrently(int threads, IndexedAction action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int index = i;
                Callable<Object> task = () -> {
                    ready.countDown();
                    start.await();
                    return action.run(index);
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

    @FunctionalInterface
    private interface IndexedAction {
        Object run(int index) throws Exception;
    }
}
