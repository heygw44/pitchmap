package com.pitchmap.basecamp.infra;

import java.time.LocalDate;

/**
 * {@link BasecampSearchMapper}가 모집 중 베이스캠프를 찾는 조건이다. area와 nearby 중 정확히 하나만 값이 있다.
 *
 * <p>fromDate와 toDate는 출발일의 범위(경계 포함)이고 null이면 그쪽은 제한하지 않는다. hasVacancy가 true이면 정원이 아직 차지 않은 베이스캠프만
 * 찾는다.
 */
public record BasecampSearchCondition(
        Area area, Nearby nearby, LocalDate fromDate, LocalDate toDate, boolean hasVacancy) {

    public BasecampSearchCondition {
        if ((area == null) == (nearby == null)) {
            throw new IllegalArgumentException("지역은 영역과 반경 중 하나만 있어야 합니다.");
        }
    }

    /** 남서(swLat, swLng)와 북동(neLat, neLng) 꼭짓점이 정하는 화면 영역이다. 경계선 위의 장소도 포함한다. */
    public record Area(double swLat, double swLng, double neLat, double neLng) {}

    /**
     * 중심(centerLat, centerLng)에서 radiusMeters 안의 장소를 찾는 조건이다. 남서·북동 꼭짓점이 만드는 후보 사각형은 공간 인덱스로 후보를 좁히는 데만 쓰므로,
     * 호출하는 쪽은 반경 원을 모두 덮을 만큼 넉넉하게 잡아야 한다.
     */
    public record Nearby(
            double centerLat,
            double centerLng,
            double radiusMeters,
            double swLat,
            double swLng,
            double neLat,
            double neLng) {}
}
