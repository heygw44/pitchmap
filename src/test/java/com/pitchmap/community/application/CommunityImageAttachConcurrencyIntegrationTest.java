package com.pitchmap.community.application;

import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.error.InvalidFieldException;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.community.infra.TestCommunityImageStorage;
import com.pitchmap.member.infra.MemberJpaRepository;
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
class CommunityImageAttachConcurrencyIntegrationTest {

    private static final int THREADS = 4;

    @Autowired
    private CommunityImageUploadService uploadService;

    @Autowired
    private CommunityPostCommandService postCommandService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private TestCommunityImageStorage storage;

    @Autowired
    private JdbcTemplate jdbc;

    @RepeatedTest(5)
    @DisplayName("[F-29][CM-06] 올린 이미지 하나를 글 여러 개에 동시에 붙이면 하나만 성공하고 나머지는 imageIds 오류이며, 이미지는 글 하나에만 붙는다")
    void sameImageAttachedByConcurrentPostsSucceedsOnce() throws Exception {
        // given
        long memberId = memberRepository.saveAndFlush(aMember().build()).getId();
        CommunityImageUpload upload = uploadService.issue(memberId, "image/jpeg", 1000L);
        storage.markUploaded(keyOf(upload.imageId()), 1000L);
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> postCommandService.write(
                    memberId, new CommunityPostWriteCommand("제목", "본문", null, List.of(upload.imageId()))));
        }

        // when
        List<Throwable> failures = runConcurrently(tasks);

        // then
        assertThat(failures)
                .hasSize(THREADS - 1)
                .allSatisfy(failure -> assertThat(failure).isInstanceOf(InvalidFieldException.class));
        assertThat(count("SELECT COUNT(*) FROM community_post")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM community_image WHERE post_id IS NOT NULL"))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT post_id FROM community_image WHERE id = ?", Long.class, upload.imageId()))
                .isEqualTo(jdbc.queryForObject("SELECT id FROM community_post", Long.class));
    }

    private String keyOf(long imageId) {
        return jdbc.queryForObject("SELECT object_key FROM community_image WHERE id = ?", String.class, imageId);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    // 작업마다 스레드 하나로 동시에 출발시키고, 던져진 예외를 모아 돌려준다.
    private List<Throwable> runConcurrently(List<Callable<Object>> tasks) throws Exception {
        int threads = tasks.size();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
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
}
