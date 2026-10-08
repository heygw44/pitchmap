package com.pitchmap.admin.api;

import com.pitchmap.trust.application.AdminMemberReportSummary;
import java.time.Instant;

/** 관리자 신고 목록의 한 항목. */
public record AdminMemberReportSummaryResponse(
        long reportId,
        String kind,
        String type,
        boolean urgent,
        String status,
        MemberRef reporter,
        MemberRef target,
        long basecampId,
        Instant createdAt) {

    public record MemberRef(long memberId, String nickname) {}

    static AdminMemberReportSummaryResponse from(AdminMemberReportSummary summary) {
        return new AdminMemberReportSummaryResponse(
                summary.reportId(),
                summary.kind(),
                summary.type(),
                summary.urgent(),
                summary.status(),
                new MemberRef(summary.reporterId(), summary.reporterNickname()),
                new MemberRef(summary.targetId(), summary.targetNickname()),
                summary.basecampId(),
                summary.createdAt());
    }
}
