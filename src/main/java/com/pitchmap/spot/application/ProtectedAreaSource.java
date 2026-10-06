package com.pitchmap.spot.application;

/**
 * 공원·보호지역 경계의 출처. 서버는 이 이름을 {@code protected_area.source}에 그대로 저장한다.
 *
 * <p>KDPA는 한국보호지역 통합DB관리시스템이다. 다른 원천의 경계를 더하면 값을 추가한다.
 */
public enum ProtectedAreaSource {
    KDPA
}
