package com.pitchmap.spot.application;

/**
 * 공원·보호지역 경계의 구분. 서버는 이 이름을 {@code protected_area.area_type}에 그대로 저장한다.
 *
 * <p>자연공원 세 종류(국립공원, 도립공원, 군립공원) 밖의 보호지역을 나중에 더할 때 OTHER를 쓴다.
 */
public enum ProtectedAreaType {
    NATIONAL_PARK,
    PROVINCIAL_PARK,
    COUNTY_PARK,
    OTHER
}
