package com.pitchmap.program.infra;

/** 결제 기한이 지나 만료할 결제 대기 신청이다. 만료 알림 이벤트에 싣는 회원과 행사를 함께 읽는다. */
public record ExpiryTarget(long applicationId, long memberId, long programId) {}
