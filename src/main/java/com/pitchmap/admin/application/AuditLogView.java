package com.pitchmap.admin.application;

import java.time.Instant;
import tools.jackson.databind.JsonNode;

/** 감사 로그 한 건. detail은 저장한 JSON 객체이고, 기록할 내용이 없었으면 null이다. */
public record AuditLogView(
        long auditLogId,
        long adminId,
        String action,
        String targetType,
        long targetId,
        JsonNode detail,
        Instant createdAt) {}
