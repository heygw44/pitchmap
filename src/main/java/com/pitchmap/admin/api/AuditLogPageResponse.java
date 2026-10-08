package com.pitchmap.admin.api;

import com.pitchmap.admin.application.AuditLogPage;
import java.util.List;

/** 감사 로그 목록의 한 페이지 응답이다. 전체 개수는 없고 다음 페이지가 있는지만 hasNext로 알려 준다. */
public record AuditLogPageResponse(List<AuditLogResponse> content, int page, int size, boolean hasNext) {

    static AuditLogPageResponse from(AuditLogPage page) {
        return new AuditLogPageResponse(
                page.content().stream().map(AuditLogResponse::from).toList(), page.page(), page.size(), page.hasNext());
    }
}
