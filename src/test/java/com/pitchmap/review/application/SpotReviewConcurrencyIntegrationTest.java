package com.pitchmap.review.application;

import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.review.domain.ReviewErrorCode;
import com.pitchmap.spot.application.SpotDetail;
import com.pitchmap.spot.application.SpotDetailQueryService;
import com.pitchmap.spot.domain.GeoPoint;
import com.pitchmap.spot.domain.ParkAreaJudgement;
import com.pitchmap.spot.domain.Spot;
import com.pitchmap.spot.infra.SpotJpaRepository;
import java.time.LocalDate;
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
class SpotReviewConcurrencyIntegrationTest {

    private static final int THREADS = 20;

    // 테스트 시계의 기본 시각은 한국 날짜로 2026-10-05 낮 12시다.
    private static final LocalDate VISITED = LocalDate.of(2026, 10, 4);

    @Autowired
    private SpotReviewCommandService commandService;

    @Autowired
    private SpotDetailQueryService spotDetailQueryService;

    @Autowired
    private SpotJpaRepository spotRepository;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @RepeatedTest(5)
    @DisplayName("[F-10] 서로 다른 회원 20명이 같은 장소에 동시에 후기를 쓰면 모두 저장되고, 상세의 후기 수와 평균이 저장된 행과 일치한다")
    void concurrentReviewsFromDifferentMembersAreAllCounted() throws Exception {
        // given
        long spotId = saveSpot();
        List<Long> memberIds = saveMembers(THREADS);

        // when
        List<Throwable> failures = runConcurrently(memberIds, memberId -> {
            int rating = ratingOf(memberIds.indexOf(memberId));
            return commandService.write(memberId, spotId, new SpotReviewWriteCommand(VISITED, rating, "평점 " + rating));
        });

        // then
        assertThat(failures).isEmpty();
        assertThat(rowCount(spotId)).isEqualTo(THREADS);
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);
        assertThat(detail.rating().count()).isEqualTo(THREADS);
        assertThat(detail.rating().average()).isEqualTo(averageOfRows(spotId));
        assertThat(detail.recentReviews()).hasSize(3);
    }

    @RepeatedTest(5)
    @DisplayName("[F-10] 같은 회원이 같은 장소에 같은 방문일로 동시에 20번 쓰면 1건만 저장되고 나머지는 SPOT_REVIEW_DUPLICATED다")
    void sameMemberSameVisitedDateIsSavedOnce() throws Exception {
        // given
        long spotId = saveSpot();
        long memberId = saveMembers(1).get(0);

        // when
        List<Throwable> failures = runConcurrently(
                Collections.nCopies(THREADS, memberId),
                id -> commandService.write(id, spotId, new SpotReviewWriteCommand(VISITED, 4, "좋다")));

        // then
        assertThat(failures)
                .hasSize(THREADS - 1)
                .allSatisfy(failure -> assertThat(failure)
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e -> assertThat(e.getErrorCode()).isEqualTo(ReviewErrorCode.SPOT_REVIEW_DUPLICATED)));
        assertThat(rowCount(spotId)).isEqualTo(1);
        assertThat(spotDetailQueryService.findDetail(spotId).rating().count()).isEqualTo(1);
    }

    // 1~5를 돌려 가며 쓰므로 평균이 정수로 떨어지지 않는 값이 나온다.
    private static int ratingOf(int index) {
        return index % 5 + 1;
    }

    private double averageOfRows(long spotId) {
        Double average = jdbc.queryForObject(
                "SELECT ROUND(AVG(rating), 1) FROM spot_review WHERE spot_id = ?", Double.class, spotId);
        return average;
    }

    private long saveSpot() {
        Spot spot = Spot.bakji(
                "능선 끝 평지",
                new GeoPoint(37.25, 127.25),
                ParkAreaJudgement.outside(MutableClock.DEFAULT_INSTANT),
                MutableClock.DEFAULT_INSTANT);
        return spotRepository.save(spot).getId();
    }

    private List<Long> saveMembers(int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(memberRepository.saveAndFlush(aMember().build()).getId());
        }
        return ids;
    }

    private int rowCount(long spotId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM spot_review WHERE spot_id = ?", Integer.class, spotId);
    }

    // 요청마다 스레드 하나로 동시에 출발시키고, 던져진 예외를 모아 돌려준다.
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
