package com.pitchmap.admin.api;

import com.pitchmap.spot.application.AdminSpotPage;
import java.util.List;

/** 관리자 박지 검토 목록의 한 페이지 응답이다. 전체 개수는 없고 다음 페이지가 있는지만 hasNext로 알려 준다. */
public record AdminSpotPageResponse(List<AdminSpotSummaryResponse> content, int page, int size, boolean hasNext) {

    static AdminSpotPageResponse from(AdminSpotPage page) {
        return new AdminSpotPageResponse(
                page.content().stream().map(AdminSpotSummaryResponse::from).toList(),
                page.page(),
                page.size(),
                page.hasNext());
    }
}
