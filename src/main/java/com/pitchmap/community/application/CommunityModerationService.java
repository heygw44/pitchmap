package com.pitchmap.community.application;

import com.pitchmap.common.audit.AdminAuditAction;
import com.pitchmap.common.audit.AdminAuditRecorder;
import com.pitchmap.common.audit.AdminAuditTargetType;
import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.community.domain.CommunityComment;
import com.pitchmap.community.domain.CommunityCommentRepository;
import com.pitchmap.community.domain.CommunityCommentStatus;
import com.pitchmap.community.domain.CommunityPost;
import com.pitchmap.community.domain.CommunityPostRepository;
import com.pitchmap.community.domain.CommunityPostStatus;
import com.pitchmap.community.domain.CommunityReportRepository;
import com.pitchmap.community.domain.CommunityReportTargetType;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자가 커뮤니티 글과 댓글을 숨기거나 복구한다.
 *
 * <p>모든 메서드는 첫 쿼리로 대상 행을 쓰기 잠금(SELECT ... FOR UPDATE)으로 읽는다. 신고도 같은 행을 먼저 잠그므로, 복구와 신고가 줄을 선다.
 * 그래서 복구 전에 커밋된 신고는 검토를 마친 것으로 표시되고, 복구 뒤의 신고는 새로 센다. 같은 대상을 동시에 숨기면 뒤 요청은 잠금을 기다린 뒤
 * 이미 숨긴 상태를 보고 거부된다. 대상이 없거나 작성자가 지운 것이면 존재를 드러내지 않으려고 NOT_FOUND로 거부하고, 상태 전이를 할 수 없으면
 * COMMUNITY_INVALID_STATE로 거부한다. 댓글은 달린 글의 상태를 보지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityModerationService {

    private final CommunityPostRepository communityPostRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final CommunityReportRepository communityReportRepository;
    private final AdminAuditRecorder adminAuditRecorder;
    private final Clock clock;

    /** 호출하면 ACTIVE나 검토 대기 글을 HIDDEN으로 바꾸고 감사 로그를 남긴다. */
    @Transactional
    public CommunityModerationResult hidePost(long postId, long adminId) {
        // 잠금 쿼리가 이 트랜잭션의 첫 쿼리여야 한다. 클래스 설명을 본다.
        CommunityPost post = lockPost(postId);
        CommunityPostStatus before = post.getStatus();
        post.hide(clock.instant());
        recordHide(
                adminId,
                AdminAuditTargetType.COMMUNITY_POST,
                postId,
                before.name(),
                post.getStatus().name());
        log.info("community post hidden postId={} adminId={}", postId, adminId);
        return new CommunityModerationResult(postId, post.getStatus().name());
    }

    /** 호출하면 검토 대기나 HIDDEN 글을 ACTIVE로 바꾸고, 그 글의 검토 전 신고를 모두 검토를 마친 것으로 표시한 뒤 감사 로그를 남긴다. */
    @Transactional
    public CommunityModerationResult restorePost(long postId, long adminId) {
        CommunityPost post = lockPost(postId);
        CommunityPostStatus before = post.getStatus();
        Instant now = clock.instant();
        post.restore(now);
        // 이 쿼리가 실행되기 전에 JPA가 위의 상태 변경을 플러시한다. 신고 엔티티는 읽지 않아서 영속성 컨텍스트를 비울 필요가 없다.
        int reviewed = communityReportRepository.markReviewed(CommunityReportTargetType.POST, postId, now);
        recordRestore(
                adminId,
                AdminAuditTargetType.COMMUNITY_POST,
                postId,
                before.name(),
                post.getStatus().name(),
                reviewed);
        log.info("community post restored postId={} adminId={} reviewedReportCount={}", postId, adminId, reviewed);
        return new CommunityModerationResult(postId, post.getStatus().name());
    }

    /** 호출하면 ACTIVE나 검토 대기 댓글을 HIDDEN으로 바꾸고 감사 로그를 남긴다. 숨겨도 내용은 지우지 않는다. */
    @Transactional
    public CommunityModerationResult hideComment(long commentId, long adminId) {
        CommunityComment comment = lockComment(commentId);
        CommunityCommentStatus before = comment.getStatus();
        comment.hide(clock.instant());
        recordHide(
                adminId,
                AdminAuditTargetType.COMMUNITY_COMMENT,
                commentId,
                before.name(),
                comment.getStatus().name());
        log.info("community comment hidden commentId={} adminId={}", commentId, adminId);
        return new CommunityModerationResult(commentId, comment.getStatus().name());
    }

    /** 호출하면 검토 대기나 HIDDEN 댓글을 ACTIVE로 바꾸고, 그 댓글의 검토 전 신고를 모두 검토를 마친 것으로 표시한 뒤 감사 로그를 남긴다. */
    @Transactional
    public CommunityModerationResult restoreComment(long commentId, long adminId) {
        CommunityComment comment = lockComment(commentId);
        CommunityCommentStatus before = comment.getStatus();
        Instant now = clock.instant();
        comment.restore(now);
        int reviewed = communityReportRepository.markReviewed(CommunityReportTargetType.COMMENT, commentId, now);
        recordRestore(
                adminId,
                AdminAuditTargetType.COMMUNITY_COMMENT,
                commentId,
                before.name(),
                comment.getStatus().name(),
                reviewed);
        log.info(
                "community comment restored commentId={} adminId={} reviewedReportCount={}",
                commentId,
                adminId,
                reviewed);
        return new CommunityModerationResult(commentId, comment.getStatus().name());
    }

    private CommunityPost lockPost(long postId) {
        return communityPostRepository
                .findByIdForUpdate(postId)
                .filter(post -> post.getStatus() != CommunityPostStatus.DELETED)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private CommunityComment lockComment(long commentId) {
        return communityCommentRepository
                .findByIdForUpdate(commentId)
                .filter(comment -> comment.getStatus() != CommunityCommentStatus.DELETED)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private void recordHide(long adminId, String targetType, long targetId, String from, String to) {
        adminAuditRecorder.record(
                adminId,
                AdminAuditAction.COMMUNITY_HIDE,
                targetType,
                targetId,
                new CommunityAuditDetails.Hide(from, to));
    }

    private void recordRestore(
            long adminId, String targetType, long targetId, String from, String to, int reviewedReportCount) {
        adminAuditRecorder.record(
                adminId,
                AdminAuditAction.COMMUNITY_RESTORE,
                targetType,
                targetId,
                new CommunityAuditDetails.Restore(from, to, reviewedReportCount));
    }
}
