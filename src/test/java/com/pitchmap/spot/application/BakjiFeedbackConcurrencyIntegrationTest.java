package com.pitchmap.spot.application;

import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.spot.domain.GeoPoint;
import com.pitchmap.spot.domain.ParkAreaJudgement;
import com.pitchmap.spot.domain.Spot;
import com.pitchmap.spot.domain.SpotErrorCode;
import com.pitchmap.spot.infra.SpotJpaRepository;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class BakjiFeedbackConcurrencyIntegrationTest {

    private static final int THREADS = 10;
    private static final int PENDING_REVIEW_REPORTS = 5;
    private static final BakjiProblemReportCommand REPORT = new BakjiProblemReportCommand("CLOSED", "길이 막혔다");

    @Autowired
    private BakjiFeedbackService bakjiFeedbackService;

    @Autowired
    private SpotJpaRepository spotRepository;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @RepeatedTest(5)
    @DisplayName("[F-08] 신고 3건인 박지에 서로 다른 회원 2명이 동시에 신고하면 둘 다 저장하고 박지가 PENDING_REVIEW가 된다")
    void concurrentReportsCrossingThresholdMoveToPendingReview() throws Exception {
        // given
        long spotId = saveBakji();
        for (int i = 0; i < PENDING_REVIEW_REPORTS - 2; i++) {
            bakjiFeedbackService.reportProblem(saveMember(), spotId, REPORT);
        }
        List<Long> memberIds = saveMembers(2);

        // when
        List<Throwable> failures = runConcurrently(memberIds, memberId -> {
            bakjiFeedbackService.reportProblem(memberId, spotId, REPORT);
            return null;
        });

        // then
        assertThat(failures).isEmpty();
        assertThat(reportCount(spotId)).isEqualTo(PENDING_REVIEW_REPORTS);
        assertThat(statusOf(spotId)).isEqualTo("PENDING_REVIEW");
    }

    @RepeatedTest(5)
    @DisplayName("[F-08] 신고가 없는 박지에 서로 다른 회원 10명이 동시에 신고하면 5건째에 PENDING_REVIEW가 되고, 그 뒤 신고는 NOT_FOUND이며 신고 행은 5건이다")
    void concurrentReportsStopAtThreshold() throws Exception {
        // given
        long spotId = saveBakji();
        List<Long> memberIds = saveMembers(THREADS);

        // when
        List<Throwable> failures = runConcurrently(memberIds, memberId -> {
            bakjiFeedbackService.reportProblem(memberId, spotId, REPORT);
            return null;
        });

        // then
        assertThat(failures)
                .hasSize(THREADS - PENDING_REVIEW_REPORTS)
                .allSatisfy(failure -> assertThat(failure)
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e -> assertThat(e.getErrorCode().name()).isEqualTo("NOT_FOUND")));
        assertThat(reportCount(spotId)).isEqualTo(PENDING_REVIEW_REPORTS);
        assertThat(statusOf(spotId)).isEqualTo("PENDING_REVIEW");
    }

    @RepeatedTest(5)
    @DisplayName("[F-08] 같은 회원이 같은 박지를 동시에 신고하면 1건만 성공하고 나머지는 BAKJI_ALREADY_REPORTED다")
    void sameMemberReportsOnlyOnce() throws Exception {
        // given
        long spotId = saveBakji();
        long memberId = saveMember();

        // when
        List<Throwable> failures = runConcurrently(sameMember(memberId), id -> {
            bakjiFeedbackService.reportProblem(id, spotId, REPORT);
            return null;
        });

        // then
        assertThat(failures)
                .hasSize(THREADS - 1)
                .allSatisfy(failure -> assertThat(failure)
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e -> assertThat(e.getErrorCode()).isEqualTo(SpotErrorCode.BAKJI_ALREADY_REPORTED)));
        assertThat(reportCount(spotId)).isEqualTo(1);
    }

    @RepeatedTest(5)
    @DisplayName("[F-08] 같은 회원이 같은 박지를 동시에 확인하면 1건만 성공하고 나머지는 BAKJI_ALREADY_CONFIRMED다")
    void sameMemberConfirmsOnlyOnce() throws Exception {
        // given
        long spotId = saveBakji();
        long memberId = saveMember();

        // when
        List<Throwable> failures =
                runConcurrently(sameMember(memberId), id -> bakjiFeedbackService.confirm(id, spotId));

        // then
        assertThat(failures)
                .hasSize(THREADS - 1)
                .allSatisfy(failure -> assertThat(failure)
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e -> assertThat(e.getErrorCode()).isEqualTo(SpotErrorCode.BAKJI_ALREADY_CONFIRMED)));
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM bakji_confirmation WHERE spot_id = ?", Integer.class, spotId))
                .isEqualTo(1);
    }

    private static List<Long> sameMember(long memberId) {
        return Collections.nCopies(THREADS, memberId);
    }

    private long saveBakji() {
        Spot spot = Spot.bakji(
                "능선 끝 평지",
                new GeoPoint(37.25, 127.25),
                ParkAreaJudgement.outside(MutableClock.DEFAULT_INSTANT),
                MutableClock.DEFAULT_INSTANT);
        return spotRepository.save(spot).getId();
    }

    private long saveMember() {
        return memberRepository.saveAndFlush(aMember().build()).getId();
    }

    private List<Long> saveMembers(int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(saveMember());
        }
        return ids;
    }

    private int reportCount(long spotId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM bakji_report WHERE spot_id = ?", Integer.class, spotId);
    }

    private String statusOf(long spotId) {
        return jdbc.queryForObject("SELECT status FROM spot WHERE id = ?", String.class, spotId);
    }

    // 회원마다 스레드 하나로 동시에 출발시키고, 던져진 예외를 모아 돌려준다.
    private <T> List<Throwable> runConcurrently(List<Long> memberIds, MemberAction<T> action) throws Exception {
        int threads = memberIds.size();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (long memberId : memberIds) {
                Callable<T> task = () -> {
                    ready.countDown();
                    start.await();
                    return action.run(memberId);
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

    private <T> List<Throwable> collectFailures(List<Future<T>> futures) throws InterruptedException {
        List<Throwable> failures = new ArrayList<>();
        for (Future<T> future : futures) {
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
    private interface MemberAction<T> {
        T run(long memberId) throws Exception;
    }
}
