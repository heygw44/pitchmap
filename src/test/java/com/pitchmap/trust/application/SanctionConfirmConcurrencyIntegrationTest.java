package com.pitchmap.trust.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.trust.domain.SanctionType;
import java.util.ArrayList;
import java.util.List;
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
class SanctionConfirmConcurrencyIntegrationTest {

    private static final long TIMEOUT_SECONDS = 30;

    @Autowired
    private SanctionConfirmService sanctionConfirmService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @RepeatedTest(5)
    @DisplayName("[SN-10] 같은 회원에게 경고를 동시에 2번 확정하면 1건만 저장되고 나머지는 INVALID_INPUT이다")
    void sameLevelIsNotConfirmedTwice() throws Exception {
        // given
        CompanionReviewFixture fixture = new CompanionReviewFixture(jdbc, memberRepository);
        long adminId = fixture.saveVerifiedMember(TestSequence.nickname());
        long targetId = fixture.saveVerifiedMember(TestSequence.nickname());
        SanctionConfirmCommand command =
                new SanctionConfirmCommand(targetId, null, SanctionType.WARNING, "반복 위반", adminId);

        // when
        List<Throwable> failures = runTwice(command);

        // then
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sanction WHERE member_id = ?", Integer.class, targetId))
                .isEqualTo(1);
    }

    @RepeatedTest(5)
    @DisplayName("[SN-10][SN-12] 같은 회원에게 7일 정지가 계산된 상태에서 동시에 2번 확정해도 정지 제재는 1건이고 데드락이 없다")
    void concurrentSuspensionsAreSerialized() throws Exception {
        // given
        CompanionReviewFixture fixture = new CompanionReviewFixture(jdbc, memberRepository);
        long adminId = fixture.saveVerifiedMember(TestSequence.nickname());
        long targetId = fixture.saveVerifiedMember(TestSequence.nickname());
        sanctionConfirmService.confirm(
                new SanctionConfirmCommand(targetId, null, SanctionType.WARNING, "첫 경고", adminId));
        SanctionConfirmCommand command =
                new SanctionConfirmCommand(targetId, null, SanctionType.SUSPEND_7D, "반복 위반", adminId);

        // when
        List<Throwable> failures = runTwice(command);

        // then
        assertThat(failures).hasSize(1);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sanction WHERE member_id = ? AND type = 'SUSPEND_7D'",
                        Integer.class,
                        targetId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM member WHERE id = ?", String.class, targetId))
                .isEqualTo("SUSPENDED");
    }

    // 같은 확정 요청을 스레드 둘로 동시에 출발시키고, 던져진 예외를 모아 돌려준다.
    private List<Throwable> runTwice(SanctionConfirmCommand command) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<SanctionConfirmResult>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return sanctionConfirmService.confirm(command);
                }));
            }
            assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Throwable> failures = new ArrayList<>();
            for (Future<SanctionConfirmResult> future : futures) {
                try {
                    future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                } catch (TimeoutException e) {
                    failures.add(e);
                }
            }
            return failures;
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .isTrue();
        }
    }
}
