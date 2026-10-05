package com.pitchmap.spot.infra;

import java.time.LocalDate;

/**
 * 이미 있는 경계 하나에 덮어쓸 값.
 *
 * @param id 바꿀 protected_area.id
 * @param sourceDate 새 원천 데이터 기준일
 * @param boundaryWkt 새 경계의 MULTIPOLYGON WKT. 좌표는 (경도 위도) 순서다.
 */
public record ProtectedAreaUpdateRow(long id, LocalDate sourceDate, String boundaryWkt) {}
