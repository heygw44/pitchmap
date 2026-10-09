package com.pitchmap.trust.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 관리자 신고 상세. companionReview는 후기 신고일 때만 있고 회원 신고이면 null이다.
 * handledBy, handledAt, resultNote는 처리 전이면 null이다.
 * 처리가 끝난 지 1년이 지나면 정리 작업이 content와 resultNote, 제재 이력의 reason을 지우므로 이 값들도 null일 수 있다.
 *
 * @param sanctionHistory 신고 대상 회원의 제재 이력(최신순). 기각된 신고가 만든 임시 정지는 뺀다
 */
public record AdminMemberReportDetail(
        AdminMemberReportSummary summary,
        String content,
        String resultNote,
        Long handledBy,
        Instant handledAt,
        Basecamp basecamp,
        Review companionReview,
        List<SanctionHistory> sanctionHistory) {

    /** 신고의 근거가 된 베이스캠프. */
    public record Basecamp(long basecampId, String title, String status, LocalDate startDate) {}

    /** 신고된 동행 후기. comment는 후기에 코멘트가 없으면 null이다. */
    public record Review(String comment, List<String> tags, boolean rejoinWanted, boolean hidden) {}

    /** 제재 이력 한 건. level은 임시 정지이면 null, endsAt은 경고와 영구 정지이면 null, liftedAt은 해제되지 않았으면 null이다. */
    public record SanctionHistory(
            long sanctionId,
            String type,
            Integer level,
            String status,
            String reason,
            Instant startsAt,
            Instant endsAt,
            Instant liftedAt) {}
}
