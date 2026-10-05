package com.pitchmap.spot.domain;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * GPS 위경도 좌표계(SRID 4326)의 위도·경도 한 점. 위도는 -90~90, 경도는 -180~180 범위의 유한한 값만 받는다.
 *
 * <p>MySQL은 SRID 4326 좌표를 WKT로 주고받을 때 위도, 경도 순서로 쓴다. 하지만 JTS {@link Point}는 x에 경도, y에 위도를 담는 것이 관례다.
 * Hibernate Spatial은 JTS 점의 x, y를 순서 그대로 MySQL 내부 저장 형식(SRID 4바이트 뒤에 WKB)으로 바꿔 바인딩하고, MySQL은 지리 좌표계의
 * 내부 저장 형식에서 축 순서 설정과 상관없이 x를 경도, y를 위도로 읽는다. 그래서 {@link #toPoint()}를 호출하면 WKT 순서와 달리 x에 경도, y에 위도가 들어간다.
 */
public record GeoPoint(double latitude, double longitude) {

    private static final int WGS84_SRID = 4326;
    private static final double MAX_ABS_LATITUDE = 90;
    private static final double MAX_ABS_LONGITUDE = 180;
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), WGS84_SRID);

    public GeoPoint {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) {
            throw new IllegalArgumentException("위도와 경도는 유한한 숫자여야 합니다.");
        }
        if (Math.abs(latitude) > MAX_ABS_LATITUDE) {
            throw new IllegalArgumentException("위도는 -90 이상 90 이하여야 합니다.");
        }
        if (Math.abs(longitude) > MAX_ABS_LONGITUDE) {
            throw new IllegalArgumentException("경도는 -180 이상 180 이하여야 합니다.");
        }
    }

    /** 호출하면 x에 경도, y에 위도를 넣은 SRID 4326 JTS 점을 돌려준다. JPA 엔티티의 공간 컬럼에는 이 점을 저장한다. */
    public Point toPoint() {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
    }

    /** 호출하면 {@link #toPoint()}가 만든 모양의 점(x는 경도, y는 위도, SRID 4326)을 좌표로 되돌린다. */
    public static GeoPoint from(Point point) {
        if (point == null || point.isEmpty()) {
            throw new IllegalArgumentException("좌표로 바꿀 점이 비어 있습니다.");
        }
        if (point.getSRID() != WGS84_SRID) {
            throw new IllegalArgumentException("SRID 4326인 점만 좌표로 바꿀 수 있습니다.");
        }
        return new GeoPoint(point.getY(), point.getX());
    }
}
