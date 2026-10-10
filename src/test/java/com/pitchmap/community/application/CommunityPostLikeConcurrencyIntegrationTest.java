package com.pitchmap.community.application;

import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.community.domain.CommunityCategory;
import com.pitchmap.community.infra.CommunityPostLikeMapper;
import com.pitchmap.member.infra.MemberJpaRepository;
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
class CommunityPostLikeConcurrencyIntegrationTest {

    private static final int THREADS = 20;
    private static final int SAME_MEMBER_PRESSES = 10;

    @Autowired
    private CommunityPostLikeService likeService;

    @Autowired
    private CommunityPostCommandService postCommandService;

    @Autowired
    private CommunityPostLikeMapper likeMapper;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @RepeatedTest(5)
    @DisplayName("[F-29][CM-05] 서로 다른 회원 20명이 같은 글에 동시에 좋아요를 누르면 예외 없이 20건이 저장되고 좋아요 수도 20이다")
    void concurrentLikesFromDifferentMembersAreAllCounted() throws Exception {
        // given
        List<Long> memberIds = saveMembers(THREADS);
        long postId = savePost(memberIds.get(0));

        // when
        List<Throwable> failures = runConcurrently(memberIds, memberId -> likeService.like(memberId, postId));

        // then
        assertThat(failures).isEmpty();
        assertThat(rowCount(postId)).isEqualTo(THREADS);
        assertThat(likeMapper.countByPost(postId)).isEqualTo(THREADS);
    }

    @RepeatedTest(5)
    @DisplayName("[F-29][CM-05] 같은 회원이 같은 글에 동시에 10번 좋아요를 눌러도 예외 없이 1건만 저장된다")
    void sameMemberLikingConcurrentlyLeavesOneRow() throws Exception {
        // given
        long memberId = saveMembers(1).get(0);
        long postId = savePost(memberId);

        // when
        List<Throwable> failures =
                runConcurrently(Collections.nCopies(SAME_MEMBER_PRESSES, memberId), id -> likeService.like(id, postId));

        // then
        assertThat(failures).isEmpty();
        assertThat(rowCount(postId)).isEqualTo(1);
        assertThat(likeMapper.countByPost(postId)).isEqualTo(1);
    }

    private long savePost(long authorId) {
        return postCommandService.write(
                authorId, new CommunityPostWriteCommand(CommunityCategory.FREE, "제목", "본문", null));
    }

    private List<Long> saveMembers(int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(memberRepository.saveAndFlush(aMember().build()).getId());
        }
        return ids;
    }

    private int rowCount(long postId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM community_post_like WHERE post_id = ?", Integer.class, postId);
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
