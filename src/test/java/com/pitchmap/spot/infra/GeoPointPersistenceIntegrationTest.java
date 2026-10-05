package com.pitchmap.spot.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.spot.domain.GeoPoint;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// 서울시청 좌표(위도 37.5665, 경도 126.978)를 쓴다. 위도와 경도가 뒤바뀌면 위도 자리에 126.978이 들어가는데,
// 위도는 -90~90 범위라서 MySQL이 값을 거부하거나 GeoPoint 생성자가 IllegalArgumentException을 던진다.
// 그래서 저장하는 쪽이든 읽는 쪽이든 축이 뒤바뀌면 아래 테스트가 실패한다.
@IntegrationTest
class GeoPointPersistenceIntegrationTest {

    private static final double SEOUL_LATITUDE = 37.5665;
    private static final double SEOUL_LONGITUDE = 126.978;
    private static final int WGS84_SRID = 4326;

    @Autowired
    private SpotLocationProbeRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("[ADR-001] JPA로 저장한 좌표를 MySQL이 같은 위도·경도로 읽는다")
    void coordinateSavedByJpaIsReadBackByMySqlWithSameLatitudeAndLongitude() {
        // given
        SpotLocationProbe probe = new SpotLocationProbe(
                "서울시청", new GeoPoint(SEOUL_LATITUDE, SEOUL_LONGITUDE), MutableClock.DEFAULT_INSTANT);

        // when
        long id = repository.save(probe).getId();
        StoredLocation stored = jdbcTemplate.queryForObject(
                "SELECT ST_Latitude(location) AS latitude, ST_Longitude(location) AS longitude,"
                        + " ST_SRID(location) AS srid, ST_AsText(location) AS wkt FROM spot WHERE id = ?",
                (rs, rowNum) -> new StoredLocation(
                        rs.getDouble("latitude"), rs.getDouble("longitude"), rs.getInt("srid"), rs.getString("wkt")),
                id);

        // then
        assertThat(stored.latitude()).isCloseTo(SEOUL_LATITUDE, within(1e-9));
        assertThat(stored.longitude()).isCloseTo(SEOUL_LONGITUDE, within(1e-9));
        assertThat(stored.srid()).isEqualTo(WGS84_SRID);
        // SRID 4326 좌표의 WKT는 위도, 경도 순서다.
        assertThat(stored.wkt()).isEqualTo("POINT(37.5665 126.978)");
    }

    @Test
    @DisplayName("[ADR-001] MySQL에 위도·경도 순서로 넣은 좌표를 JPA가 같은 GeoPoint로 읽는다")
    void coordinateInsertedAsLatitudeLongitudeWktIsReadByJpaAsSameGeoPoint() {
        // given: SRID 4326 좌표의 WKT는 위도, 경도 순서다.
        long id = insertSpotWithWkt("서울시청", "POINT(37.5665 126.978)");

        // when
        SpotLocationProbe found = repository.findById(id).orElseThrow();

        // then
        assertThat(found.getLocation()).isEqualTo(new GeoPoint(SEOUL_LATITUDE, SEOUL_LONGITUDE));
    }

    private long insertSpotWithWkt(String name, String wkt) {
        Instant now = MutableClock.DEFAULT_INSTANT;
        jdbcTemplate.update(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                        + " VALUES ('CAMPSITE', ?, ST_GeomFromText(?, ?), 60, 127, 'ACTIVE', ?, ?)",
                name,
                wkt,
                WGS84_SRID,
                now,
                now);
        Long id = jdbcTemplate.queryForObject("SELECT id FROM spot WHERE name = ?", Long.class, name);
        return id;
    }

    private record StoredLocation(double latitude, double longitude, int srid, String wkt) {}
}
