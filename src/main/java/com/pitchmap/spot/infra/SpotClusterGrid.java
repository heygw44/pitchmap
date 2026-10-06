package com.pitchmap.spot.infra;

/**
 * 장소를 묶을 때 화면 영역을 나누는 격자. cellLat과 cellLng는 칸 하나의 위도·경도 폭이고, lastCellIndex는 마지막 칸의 번호(칸 수 - 1)다.
 *
 * <p>북쪽이나 동쪽 경계선 위의 장소는 계산하면 칸 번호가 칸 수와 같아진다. 그래서 매퍼는 칸 번호를 lastCellIndex 이하로 자른다.
 */
public record SpotClusterGrid(double cellLat, double cellLng, int lastCellIndex) {}
