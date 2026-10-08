package com.pitchmap.trust.infra;

import java.time.Instant;

/** 관리자 신고 목록의 한 행. 신고자와 대상은 회원 ID와 닉네임을 함께 읽는다. */
public record AdminMemberReportRow(
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
