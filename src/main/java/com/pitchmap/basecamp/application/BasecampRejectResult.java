package com.pitchmap.basecamp.application;

/** 거절한 합류 신청의 ID와 상태 이름이다. */
public record BasecampRejectResult(long applicationId, String status) {}
