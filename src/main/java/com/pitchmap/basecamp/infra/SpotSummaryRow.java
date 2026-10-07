package com.pitchmap.basecamp.infra;

/** 베이스캠프 상세에 싣는 장소 요약이다. type은 장소 모듈의 유형 이름 문자열이다. */
public record SpotSummaryRow(long spotId, String name, String type) {}
