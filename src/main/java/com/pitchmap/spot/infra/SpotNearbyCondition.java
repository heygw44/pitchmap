package com.pitchmap.spot.infra;

import com.pitchmap.spot.domain.SpotType;
import java.util.Set;

/**
 * {@link SpotNearbyMapper}가 반경 검색에 쓰는 조건. 중심(centerLat, centerLng)에서 radiusMeters 안의 장소를 찾고, 반경 경계 위의 장소도
 * 포함한다.
 *
 * <p>남서(swLat, swLng)와 북동(neLat, neLng) 꼭짓점이 만드는 후보 사각형은 공간 인덱스로 후보를 좁히는 데만 쓴다. 그래서 호출하는 쪽은 반경 원을
 * 모두 덮을 만큼 넉넉하게 사각형을 잡아야 한다.
 *
 * <p>types가 비어 있으면 매퍼는 유형으로 거르지 않는다. hasWater와 hasToilet은 박지에만 적용하고, excludeWarning이 {@code true}면 매퍼가 공원
 * 경계 경고가 붙은 장소를 뺀다.
 */
public record SpotNearbyCondition(
        double centerLat,
        double centerLng,
        double radiusMeters,
        double swLat,
        double swLng,
        double neLat,
        double neLng,
        Set<SpotType> types,
        boolean hasWater,
        boolean hasToilet,
        boolean excludeWarning) {

    public SpotNearbyCondition {
        types = Set.copyOf(types);
    }
}
