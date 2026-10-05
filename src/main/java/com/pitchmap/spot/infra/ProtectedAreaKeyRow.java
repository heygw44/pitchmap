package com.pitchmap.spot.infra;

/**
 * 이미 저장된 경계 하나를 찾는 데 쓰는 값. MyBatis가 이름으로 매핑하므로, 구성요소 이름은 열 별칭을 camelCase로 바꾼 것과 같아야 한다.
 *
 * @param id protected_area.id
 * @param areaType 저장된 구분 enum의 이름
 * @param name 경계 이름
 */
public record ProtectedAreaKeyRow(long id, String areaType, String name) {}
