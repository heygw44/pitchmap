package com.pitchmap.spot.application;

import com.pitchmap.spot.domain.SpotType;

/**
 * 지도에 핀 하나로 그리는 장소. closedNow는 서버가 조회한 날의 한국 날짜로 계산한 휴장 여부이고, 박지는 항상 {@code false}다. 서버는 휴장 중인
 * 장소도 숨기지 않고 이 값으로 표시만 한다.
 */
public record SpotMarker(
        long spotId, SpotType type, String name, double lat, double lng, boolean parkWarning, boolean closedNow) {}
