package com.pitchmap.weather.domain;

/**
 * 중기예보 구역 하나와 대표 지점.
 *
 * @param taRegId 중기기온예보 구역 코드. 예: {@code 11B10101}(서울)
 * @param landRegId 이 구역이 속한 중기육상예보 구역 코드. 예: {@code 11B00000}(서울·인천·경기)
 * @param name 구역 이름
 * @param lat 대표 지점 위도(시·군청 위치를 어림잡은 값)
 * @param lng 대표 지점 경도
 */
public record MidTermRegion(String taRegId, String landRegId, String name, double lat, double lng) {}
