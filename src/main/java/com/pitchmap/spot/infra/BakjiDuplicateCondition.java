package com.pitchmap.spot.infra;

/**
 * {@link BakjiDuplicateMapper}가 중복 후보를 찾는 조건. 중심(centerLat, centerLng)에서 radiusMeters 안의 박지를 찾고, 반경 경계 위의 박지도
 * 포함한다. excludeSpotId가 null이 아니면 그 장소는 결과에서 뺀다.
 *
 * <p>남서(swLat, swLng)와 북동(neLat, neLng) 꼭짓점이 만드는 후보 사각형은 공간 인덱스로 후보를 좁히는 데만 쓴다. 그래서 호출하는 쪽은 반경 원을
 * 모두 덮을 만큼 넉넉하게 사각형을 잡아야 한다.
 */
public record BakjiDuplicateCondition(
        double centerLat,
        double centerLng,
        double radiusMeters,
        double swLat,
        double swLng,
        double neLat,
        double neLng,
        Long excludeSpotId,
        int limit) {}
