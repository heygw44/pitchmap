package com.pitchmap.spot.infra;

/**
 * 동기화가 이미 저장해 둔 공공데이터 장소 한 건. MyBatis가 이름으로 매핑하므로, 구성요소 이름은 열 별칭을 camelCase로 바꾼 것과 같아야 한다.
 *
 * <p>facilities는 MySQL이 정규화한 JSON 문자열이다. MySQL은 저장할 때 키 순서와 공백을 바꾸므로, 호출하는 쪽은 이 문자열을 그대로 비교하지 말고
 * 파싱해서 비교해야 한다.
 */
public record PublicSpotRow(
        long spotId,
        String externalId,
        String name,
        String address,
        double latitude,
        double longitude,
        String category,
        String facilities,
        String phone,
        String homepage) {}
