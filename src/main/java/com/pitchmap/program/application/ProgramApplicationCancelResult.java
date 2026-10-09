package com.pitchmap.program.application;

/** 신청자 본인의 취소 결과다. status는 항상 CANCELED이고, refunded는 결제를 환불했는지 여부다. */
public record ProgramApplicationCancelResult(String status, boolean refunded) {}
