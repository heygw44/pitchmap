package com.pitchmap.spot.application;

/** 장소를 숨기거나 복구한 결과. status는 처리 뒤의 장소 상태 이름이다. */
public record SpotStatusResult(long spotId, String status) {}
