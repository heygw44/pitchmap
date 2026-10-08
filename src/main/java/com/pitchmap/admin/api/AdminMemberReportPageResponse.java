package com.pitchmap.admin.api;

import com.pitchmap.trust.application.AdminMemberReportPage;
import java.util.List;

/** 관리자 신고 목록의 한 페이지 응답이다. 전체 개수는 없고 다음 페이지가 있는지만 hasNext로 알려 준다. */
public record AdminMemberReportPageResponse(
        List<AdminMemberReportSummaryResponse> content, int page, int size, boolean hasNext) {

    static AdminMemberReportPageResponse from(AdminMemberReportPage page) {
        return new AdminMemberReportPageResponse(
                page.content().stream()
                        .map(AdminMemberReportSummaryResponse::from)
                        .toList(),
                page.page(),
                page.size(),
                page.hasNext());
    }
}
