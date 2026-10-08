package com.pitchmap.admin.api;

import com.pitchmap.admin.application.AuditLogView;
import java.time.Instant;
import tools.jackson.databind.JsonNode;

/** 감사 로그 한 건의 응답. detail은 조치마다 다른 JSON 객체이고, 기록할 내용이 없었으면 null이다. */
public record AuditLogResponse(
        long auditLogId,
        long adminId,
        String action,
        String targetType,
        long targetId,
        JsonNode detail,
        Instant createdAt) {

    static AuditLogResponse from(AuditLogView view) {
        return new AuditLogResponse(
                view.auditLogId(),
                view.adminId(),
                view.action(),
                view.targetType(),
                view.targetId(),
                view.detail(),
                view.createdAt());
    }
}
