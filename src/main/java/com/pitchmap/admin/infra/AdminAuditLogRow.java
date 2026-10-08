package com.pitchmap.admin.infra;

import java.time.Instant;

/** 감사 로그 한 행. detail은 DB의 JSON 컬럼을 문자열로 읽은 값이고, 없으면 null이다. */
public record AdminAuditLogRow(
        long auditLogId,
        long adminId,
        String action,
        String targetType,
        long targetId,
        String detail,
        Instant createdAt) {}
