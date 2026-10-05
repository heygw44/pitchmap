package com.pitchmap.spot.infra;

/** 이미 저장된 공공데이터 장소 하나를 새 값으로 덮어쓸 때 넘기는 값. */
public record PublicSpotUpdateRow(long spotId, PublicSpotColumns columns) {}
