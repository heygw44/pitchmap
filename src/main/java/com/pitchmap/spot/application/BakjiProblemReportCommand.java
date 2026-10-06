package com.pitchmap.spot.application;

/** 박지 신고 요청. reason은 {@code BakjiReportReason}의 이름이고, content는 없어도 된다. */
public record BakjiProblemReportCommand(String reason, String content) {}
