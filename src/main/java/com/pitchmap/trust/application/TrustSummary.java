package com.pitchmap.trust.application;

/** 회원 한 명의 본인확인 여부와 신뢰 단계다. 다른 계층과 모듈에 넘기는 조회 결과다. */
public record TrustSummary(boolean identityVerified, int trustLevel) {}
