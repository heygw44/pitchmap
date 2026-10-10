package com.pitchmap.community.application;

import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.member.infra.MemberJpaRepository;
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
class CommunityPostViewCountConcurrencyIntegrationTest {

    private static final int MEMBERS = 15;
    private static final int ANONYMOUS_VIEWS = 5;
    private static final int OPENS_PER_VIEWER = 3;

    @Autowired
    private CommunityPostQueryService queryService;

    @Autowired
    private CommunityPostCommandService postCommandService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @RepeatedTest(5)
    @DisplayName("[F-29][CM-11] 회원 15명과 비회원 5명이 같은 글의 상세를 동시에 3번씩 열면 조회수는 정확히 60이다")
    void concurrentViewsAreAllCounted() throws Exception {
        // given
        long authorId = saveMember();
        long postId = postCommandService.write(authorId, new CommunityPostWriteCommand("제목", "본문", null));
        List<Long> viewerIds = new ArrayList<>();
        for (int i = 0; i < MEMBERS; i++) {
            viewerIds.add(saveMember());
        }
        for (int i = 0; i < ANONYMOUS_VIEWS; i++) {
            viewerIds.add(null);
        }

        // when
        List<Throwable> failures = runConcurrently(viewerIds, viewerId -> {
            for (int i = 0; i < OPENS_PER_VIEWER; i++) {
                queryService.detail(postId, viewerId);
            }
        });

        // then
        assertThat(failures).isEmpty();
        assertThat(viewCount(postId)).isEqualTo(viewerIds.size() * OPENS_PER_VIEWER);
    }

    private long saveMember() {
        return memberRepository.saveAndFlush(aMember().build()).getId();
    }

    private int viewCount(long postId) {
        return jdbc.queryForObject("SELECT view_count FROM community_post WHERE id = ?", Integer.class, postId);
    }

    // 조회하는 사람마다 스레드 하나로 동시에 출발시키고, 던져진 예외를 모아 돌려준다. 비회원은 null로 넘긴다.
    private List<Throwable> runConcurrently(List<Long> viewerIds, ViewerAction action) throws Exception {
        int threads = viewerIds.size();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Long viewerId : viewerIds) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    action.run(viewerId);
                    return null;
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

    private List<Throwable> collectFailures(List<Future<?>> futures) throws InterruptedException {
        List<Throwable> failures = new ArrayList<>();
        for (Future<?> future : futures) {
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
    private interface ViewerAction {
        void run(Long viewerId) throws Exception;
    }
}
