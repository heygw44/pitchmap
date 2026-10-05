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
