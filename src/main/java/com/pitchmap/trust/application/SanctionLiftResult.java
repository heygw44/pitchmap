package com.pitchmap.trust.application;

/** 제재를 해제한 결과. status는 해제 뒤의 제재 상태 이름이다. */
public record SanctionLiftResult(long sanctionId, String status) {}
