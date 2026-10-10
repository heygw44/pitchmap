package com.pitchmap.community.application;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.community.domain.CommunityComment;
import com.pitchmap.community.domain.CommunityCommentRepository;
import com.pitchmap.community.domain.CommunityErrorCode;
import com.pitchmap.community.domain.CommunityPost;
import com.pitchmap.community.domain.CommunityPostRepository;
import com.pitchmap.community.domain.CommunityReport;
import com.pitchmap.community.domain.CommunityReportReason;
import com.pitchmap.community.domain.CommunityReportRepository;
import com.pitchmap.community.domain.CommunityReportTargetType;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원이 커뮤니티 글이나 댓글을 신고한다. 작성자가 자기 글이나 댓글을 신고하는 것도 막지 않는다.
 *
 * <p>신고할 수 있는 글과 댓글은 목록에 보이는(ACTIVE) 것뿐이다. 없거나 ACTIVE가 아니면 서비스는 NOT_FOUND로 거부한다. 숨긴 글이 있는지
 * 드러내지 않으려고 이 경우들을 구분하지 않는다. 그래서 검토 대기로 바뀐 글과 댓글은 더 신고할 수 없다. 댓글은 달린 글도 ACTIVE여야 한다.
 *
 * <p>한 회원은 같은 글이나 댓글을 한 번만 신고한다. 서비스는 먼저 이미 했는지 조회해서 거부한다. 하지만 같은 회원의 요청 두 개가 동시에
 * 조회를 통과할 수 있으므로, 저장할 때 DB가 던지는 유니크 제약 위반도 같은 중복 오류로 바꾼다.
 *
 * <p>검토 전 신고가 {@value #PENDING_REVIEW_REPORT_THRESHOLD}건에 이르면 서비스는 같은 트랜잭션에서 대상을 검토 대기로 바꿔 목록과 상세에서
 * 뺀다. 신고 수를 따로 저장하지 않고 신고 행을 센다. 이때 관리자가 복구하면서 검토를 마친 것으로 표시한 신고는 세지 않는다. 그런데 InnoDB의
 * 기본 격리 수준(REPEATABLE READ)에서는 트랜잭션이 첫 일반 SELECT를 실행한 시점의 스냅샷을 읽는다. 그래서 4번째와 5번째 신고가 동시에
 * 들어오면 두 트랜잭션이 서로의 미커밋 행을 보지 못해 둘 다 4건으로 세고, 검토 대기로 바꾸는 일을 놓친다. 이를 막으려고 신고 트랜잭션은 첫
 * 쿼리로 대상 행을 쓰기 잠금(SELECT ... FOR UPDATE)으로 읽는다. 같은 대상의 신고가 줄을 서고, 뒤 트랜잭션은 앞 트랜잭션이 커밋한 뒤에
 * 스냅샷을 만들어 커밋된 신고를 모두 센다. 그래서 이 잠금 쿼리를 어떤 조회보다 앞에 둬야 한다.
 */
@Service
@RequiredArgsConstructor
public class CommunityReportService {

    private static final int PENDING_REVIEW_REPORT_THRESHOLD = 5;

    private final CommunityPostRepository communityPostRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final CommunityReportRepository communityReportRepository;
    private final Clock clock;

    /**
     * 호출하면 memberId인 회원의 신고를 저장한다. 저장한 뒤 검토 전 신고가 {@value #PENDING_REVIEW_REPORT_THRESHOLD}건 이상이면 글을 검토 대기로
     * 바꾼다. 글이 없거나 ACTIVE가 아니면 NOT_FOUND로, 이미 신고했으면 COMMUNITY_ALREADY_REPORTED로, 모르는 사유이면 INVALID_INPUT으로
     * 거부한다.
     */
    @Transactional
    public void reportPost(long memberId, long postId, CommunityReportCommand command) {
        // 잠금 쿼리가 이 트랜잭션의 첫 쿼리여야 한다. 클래스 설명을 본다.
        CommunityPost post = communityPostRepository
                .findByIdForUpdate(postId)
                .filter(CommunityPost::isActive)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        boolean reachedThreshold = saveReport(CommunityReportTargetType.POST, post.getId(), memberId, command);
        if (reachedThreshold) {
            post.markPendingReview(clock.instant());
        }
    }

    /**
     * 호출하면 memberId인 회원의 신고를 저장한다. 저장한 뒤 검토 전 신고가 {@value #PENDING_REVIEW_REPORT_THRESHOLD}건 이상이면 댓글을 검토 대기로
     * 바꾼다. 댓글이 없거나 ACTIVE가 아니거나 달린 글이 ACTIVE가 아니면 NOT_FOUND로, 이미 신고했으면 COMMUNITY_ALREADY_REPORTED로, 모르는
     * 사유이면 INVALID_INPUT으로 거부한다.
     */
    @Transactional
    public void reportComment(long memberId, long commentId, CommunityReportCommand command) {
        // 잠금 쿼리가 이 트랜잭션의 첫 쿼리여야 한다. 클래스 설명을 본다.
        CommunityComment comment = communityCommentRepository
                .findByIdForUpdate(commentId)
                .filter(CommunityComment::isActive)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        communityPostRepository
                .findById(comment.getPostId())
                .filter(CommunityPost::isActive)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        boolean reachedThreshold = saveReport(CommunityReportTargetType.COMMENT, comment.getId(), memberId, command);
        if (reachedThreshold) {
            comment.markPendingReview(clock.instant());
        }
    }

    // 신고를 저장하고, 저장한 뒤 검토 전 신고 수가 기준에 이르렀는지 돌려준다.
    private boolean saveReport(
            CommunityReportTargetType targetType, long targetId, long memberId, CommunityReportCommand command) {
        CommunityReportReason reason = parseReason(command.reason());
        String content = command.content();
        if (content != null && content.length() > CommunityReport.CONTENT_MAX_LENGTH) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_INPUT, "신고 내용은 " + CommunityReport.CONTENT_MAX_LENGTH + "자 이하여야 합니다.");
        }
        if (communityReportRepository.existsByTargetTypeAndTargetIdAndReporterId(targetType, targetId, memberId)) {
            throw alreadyReported();
        }
        Instant now = clock.instant();
        try {
            communityReportRepository.saveAndFlush(
                    CommunityReport.of(targetType, targetId, memberId, reason, content, now));
        } catch (DataIntegrityViolationException e) {
            throw alreadyReported();
        }
        return communityReportRepository.countByTargetTypeAndTargetIdAndReviewedAtIsNull(targetType, targetId)
                >= PENDING_REVIEW_REPORT_THRESHOLD;
    }

    private static CommunityReportReason parseReason(String value) {
        return Arrays.stream(CommunityReportReason.values())
                .filter(reason -> reason.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        CommonErrorCode.INVALID_INPUT, "신고 사유는 " + allowedReasons() + " 중 하나여야 합니다."));
    }

    private static String allowedReasons() {
        return String.join(
                ", ",
                Arrays.stream(CommunityReportReason.values()).map(Enum::name).toList());
    }

    private static BusinessException alreadyReported() {
        return new BusinessException(CommunityErrorCode.COMMUNITY_ALREADY_REPORTED);
    }
}
