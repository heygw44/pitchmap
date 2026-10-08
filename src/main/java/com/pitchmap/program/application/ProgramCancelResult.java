package com.pitchmap.program.application;

/** 행사를 취소한 결과다. canceledApplicationCount는 함께 취소된 결제 대기·확정 신청 수다. */
public record ProgramCancelResult(long programId, String status, int canceledApplicationCount) {}
