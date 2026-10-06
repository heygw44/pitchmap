package com.pitchmap.spot.application;

import com.pitchmap.spot.domain.SpotType;
import java.util.Set;

/**
 * 지도 화면 영역 조회 조건. 남서(swLat, swLng)와 북동(neLat, neLng) 꼭짓점이 만드는 사각형 안의 장소를 찾고, 경계선 위의 장소도 포함한다.
 *
 * <p>screenWidth와 screenHeight는 그 영역을 그리는 지도 화면의 크기(CSS 픽셀)다. 서버는 장소를 묶을 때 칸 크기를 정하는 데만 쓴다.
 *
 * <p>types가 비어 있으면 모든 유형을 찾는다. hasWater와 hasToilet은 박지에만 적용한다. 그래서 {@code true}여도 야영장과 자연휴양림은 시설과
 * 상관없이 남는다. excludeWarning이 {@code true}면 공원 경계 경고가 붙은 장소를 뺀다.
 */
public record SpotAreaQuery(
        double swLat,
        double swLng,
        double neLat,
        double neLng,
        int screenWidth,
        int screenHeight,
        Set<SpotType> types,
        boolean hasWater,
        boolean hasToilet,
        boolean excludeWarning) {

    public SpotAreaQuery {
        types = Set.copyOf(types);
    }
}
