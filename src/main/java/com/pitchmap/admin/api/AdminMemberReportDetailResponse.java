package com.pitchmap.admin.api;

import com.pitchmap.admin.api.AdminMemberReportSummaryResponse.MemberRef;
import com.pitchmap.trust.application.AdminMemberReportDetail;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 관리자 신고 상세. companionReview는 후기 신고일 때만 값이 있고 회원 신고이면 null이다.
 * handledBy, handledAt, resultNote는 처리 전이면 null이다.
 * 처리가 끝난 지 1년이 지나면 정리 작업이 content와 resultNote, 제재 이력의 reason을 지우므로 이 값들도 null일 수 있다.
 */
public record AdminMemberReportDetailResponse(
        long reportId,
        String kind,
        String type,
        boolean urgent,
        String status,
        MemberRef reporter,
        MemberRef target,
        long basecampId,
        Instant createdAt,
        String content,
        String resultNote,
        Long handledBy,
        Instant handledAt,
        BasecampResponse basecamp,
        CompanionReviewResponse companionReview,
        List<SanctionHistoryResponse> sanctionHistory) {

    public record BasecampResponse(long basecampId, String title, String status, LocalDate startDate) {}

    public record CompanionReviewResponse(String comment, List<String> tags, boolean rejoinWanted, boolean hidden) {}

    public record SanctionHistoryResponse(
            long sanctionId,
            String type,
            Integer level,
            String status,
            String reason,
            Instant startsAt,
            Instant endsAt,
            Instant liftedAt) {}

    static AdminMemberReportDetailResponse from(AdminMemberReportDetail detail) {
        var summary = detail.summary();
        return new AdminMemberReportDetailResponse(
                summary.reportId(),
                summary.kind(),
                summary.type(),
                summary.urgent(),
                summary.status(),
                new MemberRef(summary.reporterId(), summary.reporterNickname()),
                new MemberRef(summary.targetId(), summary.targetNickname()),
                summary.basecampId(),
                summary.createdAt(),
                detail.content(),
                detail.resultNote(),
                detail.handledBy(),
                detail.handledAt(),
                toBasecamp(detail.basecamp()),
                toReview(detail.companionReview()),
                detail.sanctionHistory().stream()
                        .map(AdminMemberReportDetailResponse::toHistory)
                        .toList());
    }

    private static BasecampResponse toBasecamp(AdminMemberReportDetail.Basecamp basecamp) {
        return new BasecampResponse(basecamp.basecampId(), basecamp.title(), basecamp.status(), basecamp.startDate());
    }

    private static CompanionReviewResponse toReview(AdminMemberReportDetail.Review review) {
        if (review == null) {
            return null;
        }
        return new CompanionReviewResponse(review.comment(), review.tags(), review.rejoinWanted(), review.hidden());
    }

    private static SanctionHistoryResponse toHistory(AdminMemberReportDetail.SanctionHistory history) {
        return new SanctionHistoryResponse(
                history.sanctionId(),
                history.type(),
                history.level(),
                history.status(),
                history.reason(),
                history.startsAt(),
                history.endsAt(),
                history.liftedAt());
    }
}
