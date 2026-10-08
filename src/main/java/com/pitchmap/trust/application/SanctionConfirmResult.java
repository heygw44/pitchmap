package com.pitchmap.trust.application;

/**
 * 확정한 제재의 결과. suspended는 제재가 회원의 이용을 막는 종류(정지)이면 true이고, 경고이면 false다.
 */
public record SanctionConfirmResult(long sanctionId, long memberId, boolean suspended) {}
