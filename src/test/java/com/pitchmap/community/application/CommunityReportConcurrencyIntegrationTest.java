package com.pitchmap.community.application;

import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.community.domain.CommunityCategory;
import com.pitchmap.community.domain.CommunityErrorCode;
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
class CommunityReportConcurrencyIntegrationTest {

    private static final int THREADS = 10;
    private static final int PENDING_REVIEW_REPORTS = 5;
    private static final CommunityReportCommand REPORT = new CommunityReportCommand("SPAM", "광고입니다");

    @Autowired
    private CommunityReportService communityReportService;

    @Autowired
    private CommunityModerationService communityModerationService;

    @Autowired
    private CommunityPostCommandService postCommandService;

    @Autowired
    private CommunityCommentCommandService commentCommandService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @RepeatedTest(5)
    @DisplayName("[F-29][CM-07] 신고 3건인 글에 서로 다른 회원 2명이 동시에 신고하면 둘 다 저장하고 글이 PENDING_REVIEW가 된다")
    void concurrentReportsCrossingThresholdMoveToPendingReview() throws Exception {
        // given
        long postId = savePost(saveMember());
        for (int i = 0; i < PENDING_REVIEW_REPORTS - 2; i++) {
            communityReportService.reportPost(saveMember(), postId, REPORT);
        }
        List<Long> memberIds = saveMembers(2);

        // when
        List<Throwable> failures = runConcurrently(memberIds, memberId -> {
            communityReportService.reportPost(memberId, postId, REPORT);
            return null;
        });

        // then
        assertThat(failures).isEmpty();
        assertThat(reportCount("POST", postId)).isEqualTo(PENDING_REVIEW_REPORTS);
        assertThat(postStatus(postId)).isEqualTo("PENDING_REVIEW");
    }

    @RepeatedTest(5)
    @DisplayName(
            "[F-29][CM-07] 신고가 없는 글에 서로 다른 회원 10명이 동시에 신고하면 5건째에 PENDING_REVIEW가 되고, 그 뒤 신고는 NOT_FOUND이며 신고 행은 5건이다")
    void concurrentReportsStopAtThreshold() throws Exception {
        // given
        long postId = savePost(saveMember());
        List<Long> memberIds = saveMembers(THREADS);

        // when
        List<Throwable> failures = runConcurrently(memberIds, memberId -> {
            communityReportService.reportPost(memberId, postId, REPORT);
            return null;
        });

        // then
        assertThat(failures)
                .hasSize(THREADS - PENDING_REVIEW_REPORTS)
                .allSatisfy(failure -> assertThat(failure)
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e -> assertThat(e.getErrorCode().name()).isEqualTo("NOT_FOUND")));
        assertThat(reportCount("POST", postId)).isEqualTo(PENDING_REVIEW_REPORTS);
        assertThat(postStatus(postId)).isEqualTo("PENDING_REVIEW");
    }

    @RepeatedTest(5)
    @DisplayName("[F-29][CM-07] 같은 회원이 같은 글을 동시에 신고하면 1건만 성공하고 나머지는 COMMUNITY_ALREADY_REPORTED다")
    void sameMemberReportsOnlyOnce() throws Exception {
        // given
        long postId = savePost(saveMember());
        long memberId = saveMember();

        // when
        List<Throwable> failures = runConcurrently(Collections.nCopies(THREADS, memberId), id -> {
            communityReportService.reportPost(id, postId, REPORT);
            return null;
        });

        // then
        assertThat(failures)
                .hasSize(THREADS - 1)
                .allSatisfy(failure -> assertThat(failure)
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e -> assertThat(e.getErrorCode())
                                        .isEqualTo(CommunityErrorCode.COMMUNITY_ALREADY_REPORTED)));
        assertThat(reportCount("POST", postId)).isEqualTo(1);
    }

    @RepeatedTest(5)
    @DisplayName("[F-29][CM-07] 신고가 없는 댓글에 서로 다른 회원 10명이 동시에 신고하면 5건째에 PENDING_REVIEW가 되고 신고 행은 5건이다")
    void concurrentCommentReportsStopAtThreshold() throws Exception {
        // given
        long postId = savePost(saveMember());
        long commentId =
                commentCommandService.write(saveMember(), postId, new CommunityCommentWriteCommand("댓글", null));
        List<Long> memberIds = saveMembers(THREADS);

        // when
        List<Throwable> failures = runConcurrently(memberIds, memberId -> {
            communityReportService.reportComment(memberId, commentId, REPORT);
            return null;
        });

        // then
        assertThat(failures)
                .hasSize(THREADS - PENDING_REVIEW_REPORTS)
                .allSatisfy(failure -> assertThat(failure)
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e -> assertThat(e.getErrorCode().name()).isEqualTo("NOT_FOUND")));
        assertThat(reportCount("COMMENT", commentId)).isEqualTo(PENDING_REVIEW_REPORTS);
        assertThat(commentStatus(commentId)).isEqualTo("PENDING_REVIEW");
    }

    @RepeatedTest(5)
    @DisplayName("[F-29][CM-07][CM-08] 관리자 숨김과 5번째 신고가 동시에 오면 글은 항상 HIDDEN이고, 신고가 숨김보다 늦으면 NOT_FOUND로만 거부된다")
    void hideRacingFifthReportEndsHidden() throws Exception {
        // given
        long postId = savePost(saveMember());
        for (int i = 0; i < PENDING_REVIEW_REPORTS - 1; i++) {
            communityReportService.reportPost(saveMember(), postId, REPORT);
        }
        long admin = saveMember();
        long fifthReporter = saveMember();

        // when
        List<Throwable> failures =
                runConcurrently(List.of(() -> communityModerationService.hidePost(postId, admin), () -> {
                    communityReportService.reportPost(fifthReporter, postId, REPORT);
                    return null;
                }));

        // then
        assertThat(postStatus(postId)).isEqualTo("HIDDEN");
        assertThat(auditCount("COMMUNITY_HIDE")).isEqualTo(1);
        if (failures.isEmpty()) {
            // 신고가 먼저 끝나 5건이 되어 검토 대기가 됐고, 그 뒤에 숨겼다.
            assertThat(reportCount("POST", postId)).isEqualTo(PENDING_REVIEW_REPORTS);
        } else {
            // 숨김이 먼저 끝나서 ACTIVE가 아닌 글의 신고가 NOT_FOUND로 거부됐다.
            assertThat(failures).hasSize(1);
            assertThat(failures.get(0))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            e -> assertThat(e.getErrorCode().name()).isEqualTo("NOT_FOUND"));
            assertThat(reportCount("POST", postId)).isEqualTo(PENDING_REVIEW_REPORTS - 1);
        }
    }

    private long savePost(long authorId) {
        return postCommandService.write(
                authorId, new CommunityPostWriteCommand(CommunityCategory.FREE, "제목", "본문", null));
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

    private int reportCount(String targetType, long targetId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM community_report WHERE target_type = ? AND target_id = ?",
                Integer.class,
                targetType,
                targetId);
    }

    private String postStatus(long postId) {
        return jdbc.queryForObject("SELECT status FROM community_post WHERE id = ?", String.class, postId);
    }

    private String commentStatus(long commentId) {
        return jdbc.queryForObject("SELECT status FROM community_comment WHERE id = ?", String.class, commentId);
    }

    private int auditCount(String action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_log WHERE action = ?", Integer.class, action);
    }

    // 회원마다 스레드 하나로 동시에 출발시키고, 던져진 예외를 모아 돌려준다.
    private <T> List<Throwable> runConcurrently(List<Long> memberIds, MemberAction<T> action) throws Exception {
        List<Callable<Object>> tasks = new ArrayList<>();
        for (long memberId : memberIds) {
            tasks.add(() -> action.run(memberId));
        }
        return runConcurrently(tasks);
    }

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

    @FunctionalInterface
    private interface MemberAction<T> {
        T run(long memberId) throws Exception;
    }
}
