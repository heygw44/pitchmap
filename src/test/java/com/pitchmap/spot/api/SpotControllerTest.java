package com.pitchmap.spot.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pitchmap.common.error.GlobalExceptionHandler;
import com.pitchmap.common.security.SecurityConfig;
import com.pitchmap.common.trace.TraceIdFilter;
import com.pitchmap.spot.application.SpotAreaQuery;
import com.pitchmap.spot.application.SpotAreaResult;
import com.pitchmap.spot.application.SpotCluster;
import com.pitchmap.spot.application.SpotMapQueryService;
import com.pitchmap.spot.application.SpotMarker;
import com.pitchmap.spot.domain.SpotType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(SpotController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class})
class SpotControllerTest {

    private static final String PATH = "/api/spots";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private SpotMapQueryService spotMapQueryService;

    @Test
    @DisplayName("[F-03] 로그인하지 않은 사용자가 영역을 조회하면 200과 함께 마커 7개 필드와 빈 묶음 배열을 응답한다")
    void anonymousAreaQueryReturnsMarkers() {
        // given
        SpotMarker bakji = new SpotMarker(101L, SpotType.BAKJI, "능선 끝 평지", 37.71, 128.75, true, false);
        SpotMarker campsite = new SpotMarker(102L, SpotType.CAMPSITE, "숲속 야영장", 37.52, 127.81, false, true);
        when(spotMapQueryService.findInArea(any())).thenReturn(new SpotAreaResult(List.of(bakji, campsite), List.of()));

        // when
        MvcTestResult result = get(validParams());

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {
                  "markers": [
                    { "spotId": 101, "type": "BAKJI", "name": "능선 끝 평지", "lat": 37.71, "lng": 128.75,
                      "parkWarning": true, "closedNow": false },
                    { "spotId": 102, "type": "CAMPSITE", "name": "숲속 야영장", "lat": 37.52, "lng": 127.81,
                      "parkWarning": false, "closedNow": true }
                  ],
                  "clusters": []
                }
                """);
    }

    @Test
    @DisplayName("[F-03] 장소가 500개를 넘어 묶음으로 조회되면 빈 마커 배열과 묶음마다 평균 좌표와 장소 수를 응답한다")
    void areaQueryReturnsClusters() {
        // given
        SpotCluster north = new SpotCluster(37.91, 127.33, 42L);
        SpotCluster south = new SpotCluster(37.12, 127.05, 480L);
        when(spotMapQueryService.findInArea(any())).thenReturn(new SpotAreaResult(List.of(), List.of(south, north)));

        // when
        MvcTestResult result = get(validParams());

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {
                  "markers": [],
                  "clusters": [
                    { "lat": 37.12, "lng": 127.05, "count": 480 },
                    { "lat": 37.91, "lng": 127.33, "count": 42 }
                  ]
                }
                """);
    }

    @Test
    @DisplayName("[F-03] 쉼표로 구분한 유형과 필터 값을 조회 조건으로 바꿔 서비스에 넘긴다")
    void filtersAreParsedIntoQuery() {
        // given
        whenServiceReturnsEmpty();
        Map<String, String> params = validParams();
        params.put("types", "CAMPSITE,BAKJI");
        params.put("hasWater", "true");
        params.put("excludeWarning", "true");

        // when
        MvcTestResult result = get(params);

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(capturedQuery())
                .isEqualTo(new SpotAreaQuery(
                        37.0, 127.0, 38.0, 128.0, Set.of(SpotType.CAMPSITE, SpotType.BAKJI), true, false, true));
    }

    @Test
    @DisplayName("[F-03] 같은 이름으로 여러 번 보낸 유형도 모두 조회 조건에 넣는다")
    void repeatedTypesAreParsedIntoQuery() {
        // given
        whenServiceReturnsEmpty();
        MockMvcRequestBuilder request = mvc.get().uri(PATH);
        validParams().forEach(request::param);

        // when
        MvcTestResult result =
                request.param("types", "FOREST").param("types", "BAKJI").exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(capturedQuery().types()).containsExactlyInAnyOrder(SpotType.FOREST, SpotType.BAKJI);
    }

    @Test
    @DisplayName("[F-03] 유형과 필터를 주지 않으면 모든 유형을 필터 없이 조회한다")
    void omittedFiltersDefaultToAllTypesWithoutFilters() {
        // given
        whenServiceReturnsEmpty();

        // when
        MvcTestResult result = get(validParams());

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(capturedQuery())
                .isEqualTo(new SpotAreaQuery(37.0, 127.0, 38.0, 128.0, Set.of(), false, false, false));
    }

    @Test
    @DisplayName("[F-03] 위도·경도 양 끝 값과 확대 수준 1·14는 받는다")
    void boundaryValuesAreAccepted() {
        // given
        whenServiceReturnsEmpty();
        Map<String, String> widest = params("-90", "-180", "90", "180", "1");
        Map<String, String> deepestZoom = params("-90", "-180", "90", "180", "14");

        // when
        MvcTestResult widestResult = get(widest);
        MvcTestResult deepestZoomResult = get(deepestZoom);

        // then
        assertThat(widestResult).hasStatus(HttpStatus.OK);
        assertThat(deepestZoomResult).hasStatus(HttpStatus.OK);
    }

    @ParameterizedTest
    @ValueSource(strings = {"swLat", "swLng", "neLat", "neLng", "zoom"})
    @DisplayName("[F-03] 영역 좌표나 확대 수준이 빠지면 그 필드 오류로 400 INVALID_INPUT을 응답하고 조회하지 않는다")
    void missingRequiredParameterReturnsFieldError(String name) {
        // given
        Map<String, String> params = validParams();
        params.remove(name);

        // when
        MvcTestResult result = get(params);

        // then
        assertInvalidInput(result, name);
    }

    @ParameterizedTest
    @CsvSource({
        "swLat, -90.1",
        "neLat, 90.1",
        "swLng, -180.1",
        "neLng, 180.1",
        "swLat, NaN",
        "zoom, 0",
        "zoom, 15",
        "zoom, 1.5",
        "swLat, abc"
    })
    @DisplayName("[F-03] 위도·경도·확대 수준이 범위를 벗어나거나 숫자가 아니면 그 필드 오류로 400 INVALID_INPUT을 응답한다")
    void outOfRangeParameterReturnsFieldError(String name, String value) {
        // given
        Map<String, String> params = validParams();
        params.put(name, value);

        // when
        MvcTestResult result = get(params);

        // then
        assertInvalidInput(result, name);
    }

    @ParameterizedTest
    @CsvSource({
        "38, 127, 38, 128, latitudeOrdered",
        "38.5, 127, 38, 128, latitudeOrdered",
        "37, 128, 38, 128, longitudeOrdered",
        "37, 128.5, 38, 128, longitudeOrdered"
    })
    @DisplayName("[F-03] 남서쪽 위도·경도가 북동쪽 값보다 작지 않으면 400 INVALID_INPUT을 응답하고 조회하지 않는다")
    void southWestNotBelowNorthEastReturnsFieldError(
            String swLat, String swLng, String neLat, String neLng, String field) {
        // when
        MvcTestResult result = get(params(swLat, swLng, neLat, neLng, "7"));

        // then
        assertInvalidInput(result, field);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN", "campsite", "CAMPSITE,PARK", "CAMPSITE,,FOREST"})
    @DisplayName("[F-03] 모르는 유형이나 빈 유형이 있으면 400 INVALID_INPUT을 응답하고 조회하지 않는다")
    void unknownTypeReturnsInvalidInput(String types) {
        // given
        Map<String, String> params = validParams();
        params.put("types", types);

        // when
        MvcTestResult result = get(params);

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field =~ /types.*/)]")
                .asList()
                .isNotEmpty();
        verifyNoInteractions(spotMapQueryService);
    }

    private void assertInvalidInput(MvcTestResult result, String field) {
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field=='" + field + "')]")
                .asList()
                .isNotEmpty();
        verifyNoInteractions(spotMapQueryService);
    }

    private void whenServiceReturnsEmpty() {
        when(spotMapQueryService.findInArea(any())).thenReturn(new SpotAreaResult(List.of(), List.of()));
    }

    private SpotAreaQuery capturedQuery() {
        ArgumentCaptor<SpotAreaQuery> captor = ArgumentCaptor.forClass(SpotAreaQuery.class);
        verify(spotMapQueryService).findInArea(captor.capture());
        return captor.getValue();
    }

    private MvcTestResult get(Map<String, String> params) {
        MockMvcRequestBuilder request = mvc.get().uri(PATH);
        params.forEach(request::param);
        return request.exchange();
    }

    private static Map<String, String> validParams() {
        return params("37", "127", "38", "128", "7");
    }

    private static Map<String, String> params(String swLat, String swLng, String neLat, String neLng, String zoom) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("swLat", swLat);
        params.put("swLng", swLng);
        params.put("neLat", neLat);
        params.put("neLng", neLng);
        params.put("zoom", zoom);
        return params;
    }
}
