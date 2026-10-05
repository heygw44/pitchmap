package com.pitchmap.spot.infra;

import com.pitchmap.spot.domain.SpotType;
import java.util.Set;

/**
 * {@link SpotAreaMapper}가 지도 영역 조회에 쓰는 조건. 남서(swLat, swLng)와 북동(neLat, neLng) 꼭짓점이 만드는 사각형이 영역이고, 경계선 위의
 * 장소도 포함한다.
 *
 * <p>types가 비어 있으면 매퍼는 유형으로 거르지 않는다. hasWater와 hasToilet은 박지에만 적용하고, excludeWarning이 {@code true}면 매퍼가 공원
 * 경계 경고가 붙은 장소를 뺀다.
 */
public record SpotAreaCondition(
        double swLat,
        double swLng,
        double neLat,
        double neLng,
        Set<SpotType> types,
        boolean hasWater,
        boolean hasToilet,
        boolean excludeWarning) {

    public SpotAreaCondition {
        types = Set.copyOf(types);
    }
}
