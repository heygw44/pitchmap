package com.pitchmap.spot.application;

import com.pitchmap.spot.domain.SpotType;

/**
 * 반경 검색 결과의 장소 한 건. distanceKm는 중심에서 장소까지의 거리를 km 단위로 소수 둘째 자리에서 반올림한 값이다.
 *
 * <p>closedNow는 서버가 조회한 날의 한국 날짜로 계산한 휴장 여부이고, 박지는 항상 {@code false}다. 서버는 휴장 중인 장소도 빼지 않고 이 값으로
 * 표시만 한다.
 */
public record SpotNearbyItem(
        long spotId,
        SpotType type,
        String name,
        double lat,
        double lng,
        boolean parkWarning,
        boolean closedNow,
        double distanceKm) {}
