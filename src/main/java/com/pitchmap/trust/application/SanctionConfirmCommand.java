package com.pitchmap.trust.application;

import com.pitchmap.trust.domain.SanctionType;

/**
 * 관리자가 회원에게 제재를 확정하려는 요청. reportId는 근거 신고이고, 신고 없이 내리는 제재이면 null이다.
 * type은 경고, 7일 정지, 30일 정지, 영구 정지 중 하나이고, 임시 정지는 요청할 수 없다.
 */
public record SanctionConfirmCommand(
        long targetMemberId, Long reportId, SanctionType type, String reason, long adminId) {}
