package com.pitchmap.admin.api;

import com.pitchmap.trust.application.MemberReportActionResult;

/** 신고 조치의 응답. 제재를 내리지 않았으면 sanctionId가 null이다. */
public record MemberReportActionResponse(long reportId, String status, Long sanctionId) {

    static MemberReportActionResponse from(MemberReportActionResult result) {
        return new MemberReportActionResponse(result.reportId(), result.status(), result.sanctionId());
    }
}
