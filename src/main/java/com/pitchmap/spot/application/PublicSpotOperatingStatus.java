package com.pitchmap.spot.application;

/**
 * 공공데이터 원천이 알려 준 장소의 운영 상태. 서버는 이 값을 {@code public_spot_detail.operating_status}에 이름 그대로 저장하고, 동기화는
 * 값의 뜻을 해석하지 않는다.
 *
 * <p>원천이 운영 상태를 주지 않았거나 모르는 값을 줬으면 상태 자체를 null로 둔다. 운영 상태가 {@code OPERATING}이어도 휴장 기간이 따로 있을 수
 * 있어서, 지금 휴장 중인지는 이 값 하나로 알 수 없다.
 */
public enum PublicSpotOperatingStatus {
    OPERATING,
    TEMPORARILY_CLOSED,
    PERMANENTLY_CLOSED
}
