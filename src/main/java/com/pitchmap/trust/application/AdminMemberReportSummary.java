package com.pitchmap.trust.application;

import java.time.Instant;

/** 관리자 신고 목록의 한 항목. kind, type, status는 각 enum의 이름이다. */
public record AdminMemberReportSummary(
        long reportId,
        String kind,
        String type,
        boolean urgent,
        String status,
        long reporterId,
        String reporterNickname,
        long targetId,
        String targetNickname,
        long basecampId,
        Instant createdAt) {}
