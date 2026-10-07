package com.pitchmap.basecamp.application;

import java.time.LocalDate;

/**
 * 모집 중 베이스캠프 검색 조건이다. 지역은 지도 영역이나 반경 중 하나이고, 출발일 범위(경계 포함)는 null이면 그쪽을 제한하지 않는다.
 * hasVacancy가 true이면 정원이 아직 차지 않은 베이스캠프만 찾는다. page는 0부터 센다.
 */
public record BasecampSearchQuery(
        Region region, LocalDate fromDate, LocalDate toDate, boolean hasVacancy, int page, int size) {

    /** 검색할 지역이다. */
    public sealed interface Region permits Area, Radius {}

    /** 남서(swLat, swLng)와 북동(neLat, neLng) 꼭짓점이 정하는 지도 영역이다. */
    public record Area(double swLat, double swLng, double neLat, double neLng) implements Region {}

    /** 중심(lat, lng)에서 radiusKm 안의 지역이다. */
    public record Radius(double lat, double lng, double radiusKm) implements Region {}
}
