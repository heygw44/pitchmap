package com.pitchmap.basecamp.application;

/** 승인한 뒤 베이스캠프의 현재 인원과 상태 이름이다. 승인해서 정원이 차면 상태는 CLOSED다. */
public record BasecampApproveResult(int headcount, String status) {}
