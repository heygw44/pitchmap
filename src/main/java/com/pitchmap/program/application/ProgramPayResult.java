package com.pitchmap.program.application;

import java.time.Instant;

/** 결제 결과다. status는 항상 CONFIRMED이고, paidAt은 결제 시각이다. */
public record ProgramPayResult(long applicationId, String status, Instant paidAt) {}
