package com.pitchmap.program.application;

import java.time.Instant;

/** 행사 신청 결과다. status는 항상 PENDING_PAYMENT이고, amount는 결제할 금액(행사 참가비)이다. */
public record ProgramApplyResult(long applicationId, String status, Instant paymentDueAt, int amount) {}
