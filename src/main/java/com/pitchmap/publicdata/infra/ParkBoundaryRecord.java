package com.pitchmap.publicdata.infra;

/**
 * 공원 경계 파일의 한 행. 리더가 형식을 검사한 뒤에 만든다.
 *
 * @param name 공원 이름. 리더가 앞뒤 공백을 지운 값이다.
 * @param areaType 공원 구분. NATIONAL_PARK, PROVINCIAL_PARK, COUNTY_PARK 중 하나다.
 * @param multiPolygonWkt 공원 경계의 MULTIPOLYGON WKT. 좌표는 (경도 위도) 순서다. 파일에 MULTIPOLYGON으로 적혀 있으면 리더가 그 문자열을 그대로
 *     담고, POLYGON으로 적혀 있으면 리더가 MULTIPOLYGON으로 감싸서 다시 쓴 문자열을 담는다.
 */
public record ParkBoundaryRecord(String name, String areaType, String multiPolygonWkt) {}
