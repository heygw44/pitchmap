package com.pitchmap.spot.infra;

/**
 * 장소를 묶을 때 화면 영역을 나누는 격자. cellLat과 cellLng는 칸 하나의 위도·경도 폭이다. lastRowIndex와 lastColumnIndex는 마지막 행과 마지막 열의
 * 번호(행 수 - 1, 열 수 - 1)이고, 행과 열의 수는 서로 다를 수 있다.
 *
 * <p>북쪽이나 동쪽 경계선 위의 장소는 계산하면 번호가 행 수나 열 수와 같아진다. 그래서 매퍼는 행 번호를 lastRowIndex 이하로, 열 번호를
 * lastColumnIndex 이하로 자른다.
 */
public record SpotClusterGrid(double cellLat, double cellLng, int lastRowIndex, int lastColumnIndex) {}
