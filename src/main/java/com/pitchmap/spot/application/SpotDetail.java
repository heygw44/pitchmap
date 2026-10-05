package com.pitchmap.spot.application;

import com.pitchmap.spot.domain.SpotType;

/**
 * 장소 상세 조회 결과. lat, lng는 위도와 경도이고, parkWarning은 장소가 공원 경계 안일 가능성이 있는지를 뜻한다.
 *
 * <p>서비스는 bakji와 publicDetail 중 장소 유형에 맞는 하나만 채운다. 박지면 bakji를, 야영장과 자연휴양림이면 publicDetail을 채우고 다른 하나는
 * {@code null}로 둔다.
 */
public record SpotDetail(
        long spotId,
        SpotType type,
        String name,
        double lat,
        double lng,
        String address,
        boolean parkWarning,
        SpotBakjiDetail bakji,
        SpotPublicDetail publicDetail) {}
