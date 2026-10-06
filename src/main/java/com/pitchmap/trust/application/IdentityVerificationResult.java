package com.pitchmap.trust.application;

/** 본인확인 결과다. 성인 기준에 못 미치면 adult가 false이고 신뢰 단계는 0이다. */
public record IdentityVerificationResult(boolean identityVerified, boolean adult, int trustLevel) {}
