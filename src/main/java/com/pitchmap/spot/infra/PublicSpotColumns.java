package com.pitchmap.spot.infra;

import java.time.LocalDate;

/**
 * 동기화가 spot과 public_spot_detail에 쓰는 값. 호출하는 쪽이 길이를 열 크기에 맞추고 빈 문자열을 null로 바꾼 뒤 넘긴다.
 *
 * <p>facilities는 JSON 문자열이고, 시설 정보가 없으면 null이다. operatingStatus는 운영 상태 enum의 이름이고, 운영 상태가 없으면 null이다.
 */
public record PublicSpotColumns(
        String externalId,
        String name,
        double latitude,
        double longitude,
        String address,
        int weatherNx,
        int weatherNy,
        String category,
        String facilities,
        String phone,
        String homepage,
        String operatingStatus,
        LocalDate closedFrom,
        LocalDate closedUntil) {}
