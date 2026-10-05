package com.pitchmap.publicdata.infra;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;

/**
 * 공원 경계 WKT 한 개를 검사하고, DB의 경계 열에 넣을 수 있는 MULTIPOLYGON WKT로 맞춘다.
 *
 * <p>DB의 경계 열은 MULTIPOLYGON만 받는다. 하지만 원천 SHP에는 조각이 하나인 공원이 POLYGON으로 들어 있을 수 있다. 그래서 이 객체는 POLYGON을
 * 조각 하나짜리 MULTIPOLYGON으로 감싸서 다시 쓴다. MULTIPOLYGON은 받은 문자열을 그대로 돌려준다. 다시 쓰면 좌표 표기가 바뀔 수 있고, 점이 수만
 * 개인 공원도 있어서 다시 쓰는 비용도 아끼기 위해서다.
 *
 * <p>JTS의 WKTReader는 스레드 안전하지 않다. 그래서 리더는 파일을 읽을 때마다 이 객체를 새로 만든다.
 */
final class ParkBoundaryWkt {

    private static final double MAX_ABS_LONGITUDE = 180;
    private static final double MAX_ABS_LATITUDE = 90;

    private final GeometryFactory geometryFactory = new GeometryFactory();
    private final WKTReader wktReader = new WKTReader(geometryFactory);
    private final WKTWriter wktWriter = new WKTWriter();

    /**
     * 호출하면 wkt를 읽어 MULTIPOLYGON WKT로 돌려준다. 좌표는 (경도 위도) 순서로 본다.
     *
     * @throws IllegalArgumentException WKT를 읽지 못했거나, 도형이 MULTIPOLYGON이나 POLYGON이 아니거나, 비어 있거나, 좌표가 경위도 범위 밖일 때
     */
    String toMultiPolygonWkt(String wkt) {
        Geometry geometry = parse(wkt);
        if (!(geometry instanceof MultiPolygon) && !(geometry instanceof Polygon)) {
            throw new IllegalArgumentException("MULTIPOLYGON이나 POLYGON이 아닙니다. type=" + geometry.getGeometryType());
        }
        if (geometry.isEmpty()) {
            throw new IllegalArgumentException("빈 도형입니다.");
        }
        requireLongitudeLatitudeRange(geometry);
        if (geometry instanceof Polygon polygon) {
            return wktWriter.write(geometryFactory.createMultiPolygon(new Polygon[] {polygon}));
        }
        return wkt;
    }

    // JTS는 고리가 닫히지 않은 도형을 만나면 ParseException이 아니라 IllegalArgumentException을 던진다. 그 예외는 그대로 호출하는 쪽에 올라간다.
    private Geometry parse(String wkt) {
        try {
            return wktReader.read(wkt);
        } catch (ParseException e) {
            throw new IllegalArgumentException("WKT를 읽지 못했습니다. " + e.getMessage(), e);
        }
    }

    // 위도와 경도를 바꿔 쓴 파일은 대부분 위도 자리에 90을 넘는 값(한국의 경도 124~132)이 들어가서 이 검사에 걸린다.
    // 이 메서드는 범위 안인 조건을 먼저 계산하고, 그 조건이 거짓이면 거부한다. NaN은 어떤 크기 비교에서도 false가 되므로, 이렇게 써야 NaN도 걸러진다.
    private static void requireLongitudeLatitudeRange(Geometry geometry) {
        for (Coordinate coordinate : geometry.getCoordinates()) {
            boolean inRange =
                    Math.abs(coordinate.getX()) <= MAX_ABS_LONGITUDE && Math.abs(coordinate.getY()) <= MAX_ABS_LATITUDE;
            if (!inRange) {
                throw new IllegalArgumentException(
                        "좌표가 경도 -180~180, 위도 -90~90 범위 밖입니다. 경도=" + coordinate.getX() + ", 위도=" + coordinate.getY());
            }
        }
    }
}
