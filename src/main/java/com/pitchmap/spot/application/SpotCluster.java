package com.pitchmap.spot.application;

/** 화면 영역을 나눈 칸 하나에 모인 장소 묶음. lat과 lng는 칸 중심 좌표이고, count는 칸 안 장소 수다. */
public record SpotCluster(double lat, double lng, long count) {}
