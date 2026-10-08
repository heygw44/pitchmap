package com.pitchmap.trust.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.infra.MemberJpaRepository;
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
class CompanionReviewConcurrencyIntegrationTest {

    @Autowired
    private CompanionReviewCommandService commandService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @RepeatedTest(5)
    @DisplayName("[F-15] 같은 상대에게 같은 후기를 동시에 2번 쓰면 1건만 저장되고 나머지는 COMPANION_REVIEW_DUPLICATED다")
    void sameReviewWrittenConcurrentlyIsSavedOnce() throws Exception {
        // given
        CompanionReviewFixture fixture = new CompanionReviewFixture(jdbc, memberRepository);
        long basecampId = fixture.saveBasecamp("COMPLETED", MutableClock.DEFAULT_INSTANT);
        long reviewee = fixture.leaderIdOf(basecampId);
        long reviewer = fixture.saveVerifiedMember("작성자");
        fixture.insertMember(basecampId, reviewer, "MEMBER", "ACTIVE");
        CompanionReviewWriteCommand command = new CompanionReviewWriteCommand(reviewee, true, List.of(), "같은 후기");

        // when
        List<Throwable> failures = runTwice(() -> commandService.write(reviewer, basecampId, command));

        // then
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(TrustErrorCode.COMPANION_REVIEW_DUPLICATED));
        assertThat(fixture.reviewCount()).isEqualTo(1);
    }

    // 같은 작업을 스레드 둘로 동시에 출발시키고, 던져진 예외를 모아 돌려준다.
    private List<Throwable> runTwice(Callable<Long> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Long>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Throwable> failures = new ArrayList<>();
            for (Future<Long> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                } catch (TimeoutException e) {
                    failures.add(e);
                }
            }
            return failures;
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }
}
