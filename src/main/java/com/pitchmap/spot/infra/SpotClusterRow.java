package com.pitchmap.spot.infra;

/** 격자 칸 하나에 모인 장소 묶음. lat과 lng는 칸 안 장소들의 평균 좌표이고, spotCount는 칸 안 장소 수다. */
public record SpotClusterRow(double lat, double lng, long spotCount) {}
