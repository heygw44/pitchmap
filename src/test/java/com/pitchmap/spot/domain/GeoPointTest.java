package com.pitchmap.spot.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

class GeoPointTest {

    private static final double SEOUL_LATITUDE = 37.5665;
    private static final double SEOUL_LONGITUDE = 126.9780;

    @ParameterizedTest(name = "위도 {0}, 경도 {1}")
    @CsvSource({"90, 0", "-90, 0", "0, 180", "0, -180", "90, 180", "-90, -180"})
    @DisplayName("위도 ±90, 경도 ±180 경계값은 허용한다")
    void acceptsBoundaryCoordinates(double latitude, double longitude) {
        // given, when
        GeoPoint point = new GeoPoint(latitude, longitude);

        // then
        assertThat(point.latitude()).isEqualTo(latitude);
        assertThat(point.longitude()).isEqualTo(longitude);
    }

    @ParameterizedTest(name = "위도 {0}, 경도 {1}")
    @CsvSource({"90.0000001, 0", "-90.0000001, 0", "0, 180.0000001", "0, -180.0000001"})
    @DisplayName("위도가 ±90, 경도가 ±180을 벗어나면 IllegalArgumentException이다")
    void rejectsCoordinatesOutOfRange(double latitude, double longitude) {
        // given, when, then
        assertThatThrownBy(() -> new GeoPoint(latitude, longitude)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "위도 {0}, 경도 {1}")
    @CsvSource({"NaN, 0", "0, NaN", "Infinity, 0", "0, -Infinity"})
    @DisplayName("위도나 경도가 NaN이거나 무한대이면 IllegalArgumentException이다")
    void rejectsNonFiniteCoordinates(double latitude, double longitude) {
        // given, when, then
        assertThatThrownBy(() -> new GeoPoint(latitude, longitude)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("JTS 점으로 바꾸면 SRID는 4326이고 x는 경도, y는 위도다")
    void toPointPutsLongitudeInXAndLatitudeInY() {
        // given
        GeoPoint seoul = new GeoPoint(SEOUL_LATITUDE, SEOUL_LONGITUDE);

        // when
        Point point = seoul.toPoint();

        // then
        assertThat(point.getSRID()).isEqualTo(4326);
        assertThat(point.getX()).isEqualTo(SEOUL_LONGITUDE);
        assertThat(point.getY()).isEqualTo(SEOUL_LATITUDE);
    }

    @Test
    @DisplayName("JTS 점으로 바꿨다가 되돌리면 같은 좌표다")
    void fromRestoresPointCreatedByToPoint() {
        // given
        GeoPoint seoul = new GeoPoint(SEOUL_LATITUDE, SEOUL_LONGITUDE);

        // when
        GeoPoint restored = GeoPoint.from(seoul.toPoint());

        // then
        assertThat(restored).isEqualTo(seoul);
    }

    @Test
    @DisplayName("SRID가 4326이 아닌 점은 좌표로 바꾸지 않고 IllegalArgumentException을 던진다")
    void fromRejectsPointWithOtherSrid() {
        // given
        Point cartesian = new GeometryFactory(new PrecisionModel(), 0)
                .createPoint(new Coordinate(SEOUL_LONGITUDE, SEOUL_LATITUDE));

        // when, then
        assertThatThrownBy(() -> GeoPoint.from(cartesian)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null이나 빈 점은 좌표로 바꾸지 않고 IllegalArgumentException을 던진다")
    void fromRejectsNullOrEmptyPoint() {
        // given
        Point empty = new GeometryFactory(new PrecisionModel(), 4326).createPoint();

        // when, then
        assertThatThrownBy(() -> GeoPoint.from(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GeoPoint.from(empty)).isInstanceOf(IllegalArgumentException.class);
    }
}
