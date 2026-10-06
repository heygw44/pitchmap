package com.pitchmap.spot.infra;

import java.time.LocalDate;

/**
 * 새로 추가할 경계 하나.
 *
 * @param source 출처 enum의 이름
 * @param areaType 구분 enum의 이름
 * @param name 경계 이름
 * @param sourceDate 원천 데이터 기준일
 * @param boundaryWkt 경계의 MULTIPOLYGON WKT. 좌표는 (경도 위도) 순서다.
 */
public record ProtectedAreaInsertRow(
        String source, String areaType, String name, LocalDate sourceDate, String boundaryWkt) {}
