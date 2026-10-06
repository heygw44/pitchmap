package com.pitchmap.spot.application;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.spot.domain.SpotErrorCode;
import com.pitchmap.spot.domain.SpotType;
import java.util.Set;

/**
 * 반경 검색 조건. 중심(lat, lng)에서 radiusKm 안에 있는 장소를 찾고, page(0부터)와 size로 한 페이지를 고른다.
 *
 * <p>반경이 {@value #MAX_RADIUS_KM}km를 넘으면 생성자가 {@link SpotErrorCode#SPOT_RADIUS_TOO_LARGE}로 거부한다. 그래서 컨트롤러가 요청을 이
 * 조건으로 바꾸는 순간 큰 반경은 서비스까지 가지 않는다.
 *
 * <p>필터는 지도 영역 조회와 같다. types가 비어 있으면 모든 유형을 찾고, hasWater와 hasToilet은 박지에만 적용한다. excludeWarning이
 * {@code true}면 공원 경계 경고가 붙은 장소를 뺀다.
 */
public record SpotNearbyQuery(
        double lat,
        double lng,
        double radiusKm,
        Set<SpotType> types,
        boolean hasWater,
        boolean hasToilet,
        boolean excludeWarning,
        int page,
        int size) {

    public static final double MAX_RADIUS_KM = 50;

    public SpotNearbyQuery {
        types = Set.copyOf(types);
        if (radiusKm > MAX_RADIUS_KM) {
            throw new BusinessException(SpotErrorCode.SPOT_RADIUS_TOO_LARGE);
        }
    }
}
