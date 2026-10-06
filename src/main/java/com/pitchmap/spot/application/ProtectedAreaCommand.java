package com.pitchmap.spot.application;

import java.util.Objects;

/**
 * 적재할 공원·보호지역 경계 하나.
 *
 * @param name 경계 이름. 같은 출처와 구분 안에서 이 이름으로 기존 경계를 찾는다.
 * @param areaType 경계 구분
 * @param multiPolygonWkt 경계의 MULTIPOLYGON WKT. 좌표는 (경도 위도) 순서여야 한다. 호출하는 쪽이 형식을 검사해서 넘긴다.
 */
public record ProtectedAreaCommand(String name, ProtectedAreaType areaType, String multiPolygonWkt) {

    public ProtectedAreaCommand {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(areaType, "areaType");
        Objects.requireNonNull(multiPolygonWkt, "multiPolygonWkt");
    }
}
