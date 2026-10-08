package com.pitchmap.trust.application;

/** 신고를 검토 시작하거나 기각한 결과. status는 처리 뒤의 신고 상태 이름이다. */
public record MemberReportStatusResult(long reportId, String status) {}
