package com.pitchmap.spot.application;

import java.util.Map;

/**
 * 공공데이터 원천의 장소 한 건. 외부 ID와 이름을 뺀 문자열 필드는 null이어도 된다.
 *
 * <p>facilities는 원천 항목 이름을 키로, 그 값을 값으로 담는다. null이나 빈 맵을 넘기면 서버는
 * {@code public_spot_detail.facilities}를 NULL로 저장한다.
 */
public record PublicSpotCommand(
        String externalId,
        String name,
        double latitude,
        double longitude,
        String address,
        String category,
        Map<String, String> facilities,
        String phone,
        String homepage) {}
