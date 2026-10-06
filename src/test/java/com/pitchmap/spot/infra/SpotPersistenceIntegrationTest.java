package com.pitchmap.spot.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.spot.domain.GeoPoint;
import com.pitchmap.spot.domain.ParkAreaJudgement;
import com.pitchmap.spot.domain.Spot;
import com.pitchmap.spot.domain.SpotRepository;
import com.pitchmap.spot.domain.WeatherGrid;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SpotPersistenceIntegrationTest {

    // 북한산국립공원 안쪽 좌표다.
    private static final GeoPoint BUKHANSAN = new GeoPoint(37.66, 126.98);
    // 위 좌표를 덮는 사각형 경계(경도 126.9~127.1, 위도 37.6~37.7)다. 공원 경계 파일과 같은 경도, 위도 순서로 적는다.
    private static final String BUKHANSAN_BOUNDARY_WKT =
            "MULTIPOLYGON(((126.9 37.6, 127.1 37.6, 127.1 37.7, 126.9 37.7, 126.9 37.6)))";

    @Autowired
    private SpotRepository spotRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("[F-07] 공원 경계 안으로 판정한 박지를 저장하면 경고, 경계 ID, 판정 시각이 spot 행에 남는다")
    void savesBakjiJudgedInsideParkBoundary() {
        // given
        long protectedAreaId = insertProtectedArea("북한산국립공원", BUKHANSAN_BOUNDARY_WKT);
        Instant now = MutableClock.DEFAULT_INSTANT;
        Instant checkedAt = now.minusSeconds(1);
        Spot bakji = Spot.bakji("능선 아래 평지", BUKHANSAN, ParkAreaJudgement.inside(protectedAreaId, checkedAt), now);

        // when
        long id = spotRepository.save(bakji).getId();
        StoredSpot stored = findStoredSpot(id);

        // then
        assertThat(stored.type()).isEqualTo("BAKJI");
        assertThat(stored.status()).isEqualTo("ACTIVE");
        WeatherGrid expectedGrid = WeatherGrid.from(BUKHANSAN);
        assertThat(stored.weatherNx()).isEqualTo(expectedGrid.nx());
        assertThat(stored.weatherNy()).isEqualTo(expectedGrid.ny());
        assertThat(stored.parkWarning()).isTrue();
        assertThat(stored.protectedAreaId()).isEqualTo(protectedAreaId);
        assertThat(stored.areaCheckedAt()).isEqualTo(checkedAt);
    }

    @Test
    @DisplayName("[F-07] 공원 경계 밖으로 판정한 박지를 저장하면 경고와 경계 ID는 비우고 판정 시각만 남긴다")
    void savesBakjiJudgedOutsideParkBoundary() {
        // given
        Instant now = MutableClock.DEFAULT_INSTANT;
        Spot bakji = Spot.bakji("강변 자갈밭", BUKHANSAN, ParkAreaJudgement.outside(now), now);

        // when
        long id = spotRepository.save(bakji).getId();
        StoredSpot stored = findStoredSpot(id);

        // then
        assertThat(stored.parkWarning()).isFalse();
        assertThat(stored.protectedAreaId()).isNull();
        assertThat(stored.areaCheckedAt()).isEqualTo(now);
    }

    private long insertProtectedArea(String name, String boundaryWkt) {
        jdbcTemplate.update(
                "INSERT INTO protected_area (name, area_type, source, source_date, boundary, created_at, updated_at)"
                        + " VALUES (?, 'NATIONAL_PARK', 'KDPA', '2026-01-01',"
                        + " ST_GeomFromText(?, 4326, 'axis-order=long-lat'), NOW(6), NOW(6))",
                name,
                boundaryWkt);
        return jdbcTemplate.queryForObject("SELECT id FROM protected_area WHERE name = ?", Long.class, name);
    }

    // DATETIME 컬럼에는 UTC 시각이 들어 있으므로(connectionTimeZone=UTC) LocalDateTime을 UTC로 읽어 Instant로 바꾼다.
    private StoredSpot findStoredSpot(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT type, status, weather_nx, weather_ny, park_warning, protected_area_id, area_checked_at"
                        + " FROM spot WHERE id = ?",
                (rs, rowNum) -> new StoredSpot(
                        rs.getString("type"),
                        rs.getString("status"),
                        rs.getInt("weather_nx"),
                        rs.getInt("weather_ny"),
                        rs.getBoolean("park_warning"),
                        rs.getObject("protected_area_id", Long.class),
                        rs.getObject("area_checked_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)),
                id);
    }

    private record StoredSpot(
            String type,
            String status,
            int weatherNx,
            int weatherNy,
            boolean parkWarning,
            Long protectedAreaId,
            Instant areaCheckedAt) {}
}
