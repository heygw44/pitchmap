package com.pitchmap.spot.application;

/**
 * 공공데이터 장소의 출처. 서버는 이 이름을 {@code public_spot_detail.source}에 그대로 저장한다.
 *
 * <p>출처마다 장소 종류가 하나로 정해져 있다. 고캠핑 야영장은 {@code CAMPSITE}, 자연휴양림은 {@code FOREST}로 저장한다.
 */
public enum PublicSpotSource {
    GOCAMPING("CAMPSITE"),
    FOREST("FOREST");

    private final String spotType;

    PublicSpotSource(String spotType) {
        this.spotType = spotType;
    }

    String spotType() {
        return spotType;
    }
}
