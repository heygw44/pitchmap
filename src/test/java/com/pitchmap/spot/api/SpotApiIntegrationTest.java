package com.pitchmap.spot.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.trace.TraceIdFilter;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@AutoConfigureMockMvc
class SpotApiIntegrationTest {

    private static final String PATH = "/api/spots";
    private static final String NEARBY_PATH = "/api/spots/nearby";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("[F-03] 로그인하지 않은 사용자가 CSRF 토큰 없이 영역을 조회하면 영역 안 장소만 마커로 받고, 휴장 중인 야영장도 숨기지 않는다")
    void anonymousReadsMarkersInsideArea() {
        // given: 시계는 한국 날짜 2026-10-05이고, 영역 안 야영장은 그날을 포함한 휴장 기간이 있다.
        long insideId = insertCampsite("영역 안 야영장", 37.5, 127.5);
        insertPublicDetail(insideId, "GC-INSIDE", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        long outsideId = insertCampsite("영역 밖 야영장", 35.1, 129.0);
        insertPublicDetail(outsideId, "GC-OUTSIDE", null, null);

        // when
        MvcTestResult result = mvc.get()
                .uri(PATH)
                .param("swLat", "37")
                .param("swLng", "127")
                .param("neLat", "38")
                .param("neLng", "128")
                .param("zoom", "7")
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.markers").asList().hasSize(1);
        assertThat(result).bodyJson().extractingPath("$.markers[0].spotId").isEqualTo((int) insideId);
        assertThat(result).bodyJson().extractingPath("$.markers[0].type").isEqualTo("CAMPSITE");
        assertThat(result).bodyJson().extractingPath("$.markers[0].name").isEqualTo("영역 안 야영장");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.markers[0].lat")
                .asNumber()
                .satisfies(lat -> assertThat(lat.doubleValue()).isCloseTo(37.5, within(1e-7)));
        assertThat(result)
                .bodyJson()
                .extractingPath("$.markers[0].lng")
                .asNumber()
                .satisfies(lng -> assertThat(lng.doubleValue()).isCloseTo(127.5, within(1e-7)));
        assertThat(result).bodyJson().extractingPath("$.markers[0].parkWarning").isEqualTo(false);
        assertThat(result).bodyJson().extractingPath("$.markers[0].closedNow").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.clusters").asList().isEmpty();
    }

    @Test
    @DisplayName("[F-03] 남서쪽 위도가 북동쪽 위도보다 크면 400 INVALID_INPUT과 필드 오류, traceId를 응답한다")
    void invalidAreaReturnsInvalidInputWithTraceId() {
        // when
        MvcTestResult result = mvc.get()
                .uri(PATH)
                .param("swLat", "38")
                .param("swLng", "127")
                .param("neLat", "37")
                .param("neLng", "128")
                .param("zoom", "7")
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field=='latitudeOrdered')]")
                .asList()
                .isNotEmpty();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.traceId")
                .isEqualTo(result.getResponse().getHeader(TraceIdFilter.HEADER));
        assertThat(result).bodyJson().extractingPath("$.traceId").asString().isNotBlank();
    }

    @Test
    @DisplayName("[F-04] 로그인하지 않은 사용자가 반경 검색을 하면 반경 안 장소만 가까운 순서와 거리로 받고, 휴장 중인 야영장도 숨기지 않는다")
    void anonymousReadsNearbySpotsInDistanceOrder() {
        // given: 중심은 (37.5, 127.0)이고 모든 장소는 같은 경도에 있다. 위도 0.01도는 약 1.11km다.
        // 시계는 한국 날짜 2026-10-05이고, 중간 거리의 야영장은 그날을 포함한 휴장 기간이 있다.
        long farId = insertCampsite("5km 야영장", 37.55, 127.0);
        insertPublicDetail(farId, "GC-FAR", null, null);
        long nearId = insertCampsite("1km 야영장", 37.51, 127.0);
        insertPublicDetail(nearId, "GC-NEAR", null, null);
        long closedId = insertCampsite("3km 휴장 야영장", 37.53, 127.0);
        insertPublicDetail(closedId, "GC-CLOSED", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        long outsideId = insertCampsite("11km 야영장", 37.6, 127.0);
        insertPublicDetail(outsideId, "GC-OUTSIDE", null, null);

        // when
        MvcTestResult result = mvc.get()
                .uri(NEARBY_PATH)
                .param("lat", "37.5")
                .param("lng", "127.0")
                .param("radiusKm", "10")
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.content").asList().hasSize(3);
        assertThat(result).bodyJson().extractingPath("$.content[0].spotId").isEqualTo((int) nearId);
        assertThat(result).bodyJson().extractingPath("$.content[1].spotId").isEqualTo((int) closedId);
        assertThat(result).bodyJson().extractingPath("$.content[2].spotId").isEqualTo((int) farId);
        assertDistanceKm(result, 0, 1.11);
        assertDistanceKm(result, 1, 3.34);
        assertDistanceKm(result, 2, 5.56);
        assertThat(result).bodyJson().extractingPath("$.content[0].type").isEqualTo("CAMPSITE");
        assertThat(result).bodyJson().extractingPath("$.content[0].name").isEqualTo("1km 야영장");
        assertThat(result).bodyJson().extractingPath("$.content[0].parkWarning").isEqualTo(false);
        assertThat(result).bodyJson().extractingPath("$.content[0].closedNow").isEqualTo(false);
        assertThat(result).bodyJson().extractingPath("$.content[1].closedNow").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.page").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.size").isEqualTo(20);
        assertThat(result).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("[F-04] 반경 검색 결과가 페이지 크기보다 많으면 hasNext가 true이고, 다음 페이지에 남은 장소를 이어서 준다")
    void nearbyResultsArePaged() {
        // given
        long firstId = insertCampsite("첫째 야영장", 37.51, 127.0);
        insertPublicDetail(firstId, "GC-FIRST", null, null);
        long secondId = insertCampsite("둘째 야영장", 37.52, 127.0);
        insertPublicDetail(secondId, "GC-SECOND", null, null);
        long thirdId = insertCampsite("셋째 야영장", 37.53, 127.0);
        insertPublicDetail(thirdId, "GC-THIRD", null, null);

        // when
        MvcTestResult firstPage = mvc.get()
                .uri(NEARBY_PATH)
                .param("lat", "37.5")
                .param("lng", "127.0")
                .param("radiusKm", "10")
                .param("size", "2")
                .exchange();
        MvcTestResult secondPage = mvc.get()
                .uri(NEARBY_PATH)
                .param("lat", "37.5")
                .param("lng", "127.0")
                .param("radiusKm", "10")
                .param("page", "1")
                .param("size", "2")
                .exchange();

        // then
        assertThat(firstPage).hasStatus(HttpStatus.OK);
        assertThat(firstPage).bodyJson().extractingPath("$.content").asList().hasSize(2);
        assertThat(firstPage).bodyJson().extractingPath("$.content[0].spotId").isEqualTo((int) firstId);
        assertThat(firstPage).bodyJson().extractingPath("$.content[1].spotId").isEqualTo((int) secondId);
        assertThat(firstPage).bodyJson().extractingPath("$.page").isEqualTo(0);
        assertThat(firstPage).bodyJson().extractingPath("$.size").isEqualTo(2);
        assertThat(firstPage).bodyJson().extractingPath("$.hasNext").isEqualTo(true);
        assertThat(secondPage).hasStatus(HttpStatus.OK);
        assertThat(secondPage).bodyJson().extractingPath("$.content").asList().hasSize(1);
        assertThat(secondPage).bodyJson().extractingPath("$.content[0].spotId").isEqualTo((int) thirdId);
        assertThat(secondPage).bodyJson().extractingPath("$.page").isEqualTo(1);
        assertThat(secondPage).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("[F-04] 반경이 50km를 넘으면 400 SPOT_RADIUS_TOO_LARGE와 traceId를 응답한다")
    void radiusOverFiftyKmReturnsRadiusTooLarge() {
        // when
        MvcTestResult result = mvc.get()
                .uri(NEARBY_PATH)
                .param("lat", "37.5")
                .param("lng", "127.0")
                .param("radiusKm", "51")
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("SPOT_RADIUS_TOO_LARGE");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.traceId")
                .isEqualTo(result.getResponse().getHeader(TraceIdFilter.HEADER));
    }

    // 서버는 거리를 소수 둘째 자리까지 반올림해서 준다. 구면 거리 계산에 쓰는 지구 반지름 값에 따라
    // 마지막 자리가 달라질 수 있어서, 테스트는 0.01km 남짓한 오차를 허용한다.
    private static void assertDistanceKm(MvcTestResult result, int index, double expectedKm) {
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[" + index + "].distanceKm")
                .asNumber()
                .satisfies(distance -> assertThat(distance.doubleValue()).isCloseTo(expectedKm, within(0.011)));
    }

    // SRID 4326 점의 내부 저장 형식은 x가 경도, y가 위도라서 POINT에는 경도를 먼저 넘긴다.
    private long insertCampsite(String name, double latitude, double longitude) {
        Instant now = MutableClock.DEFAULT_INSTANT;
        jdbc.update(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                        + " VALUES ('CAMPSITE', ?, ST_SRID(POINT(?, ?), 4326), 60, 127, 'ACTIVE', ?, ?)",
                name,
                longitude,
                latitude,
                now,
                now);
        return jdbc.queryForObject("SELECT id FROM spot WHERE name = ?", Long.class, name);
    }

    private void insertPublicDetail(long spotId, String externalId, LocalDate closedFrom, LocalDate closedUntil) {
        Instant now = MutableClock.DEFAULT_INSTANT;
        jdbc.update(
                "INSERT INTO public_spot_detail (spot_id, source, external_id, operating_status, closed_from,"
                        + " closed_until, synced_at, created_at, updated_at)"
                        + " VALUES (?, 'GOCAMPING', ?, 'OPERATING', ?, ?, ?, ?, ?)",
                spotId,
                externalId,
                closedFrom,
                closedUntil,
                now,
                now,
                now);
    }
}
