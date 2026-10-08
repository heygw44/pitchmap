package com.pitchmap.spot.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.error.GlobalExceptionHandler;
import com.pitchmap.common.security.SecurityConfig;
import com.pitchmap.common.trace.TraceIdFilter;
import com.pitchmap.spot.application.PublicSpotOperatingStatus;
import com.pitchmap.spot.application.PublicSpotSource;
import com.pitchmap.spot.application.SpotAreaQuery;
import com.pitchmap.spot.application.SpotAreaResult;
import com.pitchmap.spot.application.SpotBakjiDetail;
import com.pitchmap.spot.application.SpotCluster;
import com.pitchmap.spot.application.SpotDetail;
import com.pitchmap.spot.application.SpotDetailQueryService;
import com.pitchmap.spot.application.SpotExpectedPeople;
import com.pitchmap.spot.application.SpotFacilities;
import com.pitchmap.spot.application.SpotJoinCondition;
import com.pitchmap.spot.application.SpotMapQueryService;
import com.pitchmap.spot.application.SpotMarker;
import com.pitchmap.spot.application.SpotNearbyItem;
import com.pitchmap.spot.application.SpotNearbyPage;
import com.pitchmap.spot.application.SpotNearbyQuery;
import com.pitchmap.spot.application.SpotNearbyQueryService;
import com.pitchmap.spot.application.SpotParkWarning;
import com.pitchmap.spot.application.SpotPublicDetail;
import com.pitchmap.spot.application.SpotRating;
import com.pitchmap.spot.application.SpotRecentReview;
import com.pitchmap.spot.application.SpotRecruitingBasecamp;
import com.pitchmap.spot.application.SpotWeather;
import com.pitchmap.spot.domain.SpotType;
import com.pitchmap.weather.application.SunTimes;
import com.pitchmap.weather.application.WeatherForecast;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
    private static final String NEARBY_PATH = "/api/spots/nearby";

    private static final SpotParkWarning WARNED = new SpotParkWarning(
            true,
            "설악산 국립공원",
            "KDPA",
            LocalDate.of(2026, 1, 1),
            "참고용 데이터입니다. 공식 경계는 고시 도면을 확인하세요.",
            "자연공원 안에서는 지정된 야영장 밖의 야영과 취사가 금지돼 있고, 어기면 과태료 대상입니다. 이곳에서 야영하지 말고 가까운 지정 야영장을 이용하세요.");
    private static final SpotRating NO_RATING = new SpotRating(null, 0);
    private static final SpotParkWarning NOT_WARNED = new SpotParkWarning(false, null, null, null, null, null);

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private SpotMapQueryService spotMapQueryService;

    @MockitoBean
    private SpotNearbyQueryService spotNearbyQueryService;

    @MockitoBean
    private SpotDetailQueryService spotDetailQueryService;

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

    @Test
    @DisplayName("[F-04] 로그인하지 않은 사용자가 CSRF 토큰 없이 반경 검색을 하면 200과 함께 장소 8개 필드와 페이지 정보를 응답한다")
    void anonymousNearbyQueryReturnsPage() {
        // given
        SpotNearbyItem campsite =
                new SpotNearbyItem(102L, SpotType.CAMPSITE, "숲속 야영장", 37.52, 127.01, false, true, 2.23);
        SpotNearbyItem bakji = new SpotNearbyItem(101L, SpotType.BAKJI, "능선 끝 평지", 37.61, 127.05, true, false, 12.5);
        when(spotNearbyQueryService.findNearby(any()))
                .thenReturn(new SpotNearbyPage(List.of(campsite, bakji), 0, 20, true));

        // when
        MvcTestResult result = getNearby(validNearbyParams());

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {
                  "content": [
                    { "spotId": 102, "type": "CAMPSITE", "name": "숲속 야영장", "lat": 37.52, "lng": 127.01,
                      "parkWarning": false, "closedNow": true, "distanceKm": 2.23 },
                    { "spotId": 101, "type": "BAKJI", "name": "능선 끝 평지", "lat": 37.61, "lng": 127.05,
                      "parkWarning": true, "closedNow": false, "distanceKm": 12.5 }
                  ],
                  "page": 0,
                  "size": 20,
                  "hasNext": true
                }
                """);
    }

    @Test
    @DisplayName("[F-04] 중심 좌표와 반경만 주면 모든 유형을 필터 없이 첫 페이지 20개로 조회한다")
    void omittedNearbyOptionsUseDefaults() {
        // given
        whenNearbyServiceReturnsEmpty();

        // when
        MvcTestResult result = getNearby(validNearbyParams());

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(capturedNearbyQuery())
                .isEqualTo(new SpotNearbyQuery(37.5, 127.0, 10.0, Set.of(), false, false, false, 0, 20));
    }

    @Test
    @DisplayName("[F-04] 쉼표로 구분한 유형, 필터 값, 페이지 번호와 크기를 조회 조건으로 바꿔 서비스에 넘긴다")
    void nearbyFiltersAndPagingAreParsedIntoQuery() {
        // given
        whenNearbyServiceReturnsEmpty();
        Map<String, String> params = validNearbyParams();
        params.put("types", "CAMPSITE,BAKJI");
        params.put("hasToilet", "true");
        params.put("excludeWarning", "true");
        params.put("page", "2");
        params.put("size", "50");

        // when
        MvcTestResult result = getNearby(params);

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(capturedNearbyQuery())
                .isEqualTo(new SpotNearbyQuery(
                        37.5, 127.0, 10.0, Set.of(SpotType.CAMPSITE, SpotType.BAKJI), false, true, true, 2, 50));
    }

    @Test
    @DisplayName("[F-04] 같은 이름으로 여러 번 보낸 유형도 모두 반경 검색 조건에 넣는다")
    void repeatedTypesAreParsedIntoNearbyQuery() {
        // given
        whenNearbyServiceReturnsEmpty();
        MockMvcRequestBuilder request = mvc.get().uri(NEARBY_PATH);
        validNearbyParams().forEach(request::param);

        // when
        MvcTestResult result =
                request.param("types", "FOREST").param("types", "BAKJI").exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(capturedNearbyQuery().types()).containsExactlyInAnyOrder(SpotType.FOREST, SpotType.BAKJI);
    }

    @Test
    @DisplayName("[F-04] 반경이 정확히 50km이면 받는다")
    void radiusOfFiftyKmIsAccepted() {
        // given
        whenNearbyServiceReturnsEmpty();
        Map<String, String> params = validNearbyParams();
        params.put("radiusKm", "50");

        // when
        MvcTestResult result = getNearby(params);

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(capturedNearbyQuery().radiusKm()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("[F-04] 반경이 50km를 넘으면 400 SPOT_RADIUS_TOO_LARGE를 응답하고 조회하지 않는다")
    void radiusOverFiftyKmReturnsRadiusTooLarge() {
        // given
        Map<String, String> params = validNearbyParams();
        params.put("radiusKm", "50.1");

        // when
        MvcTestResult result = getNearby(params);

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("SPOT_RADIUS_TOO_LARGE");
        verifyNoInteractions(spotNearbyQueryService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"lat", "lng", "radiusKm"})
    @DisplayName("[F-04] 중심 좌표나 반경이 빠지면 그 필드 오류로 400 INVALID_INPUT을 응답하고 조회하지 않는다")
    void missingNearbyParameterReturnsFieldError(String name) {
        // given
        Map<String, String> params = validNearbyParams();
        params.remove(name);

        // when
        MvcTestResult result = getNearby(params);

        // then
        assertNearbyInvalidInput(result, name);
    }

    @ParameterizedTest
    @CsvSource({
        "radiusKm, 0",
        "radiusKm, -1",
        "radiusKm, abc",
        "lat, 90.1",
        "lat, -90.1",
        "lng, 180.1",
        "lng, -180.1",
        "lat, abc",
        "page, -1",
        "page, abc",
        "size, 0",
        "size, 51"
    })
    @DisplayName("[F-04] 반경·좌표·페이지 값이 범위를 벗어나거나 숫자가 아니면 그 필드 오류로 400 INVALID_INPUT을 응답한다")
    void outOfRangeNearbyParameterReturnsFieldError(String name, String value) {
        // given
        Map<String, String> params = validNearbyParams();
        params.put(name, value);

        // when
        MvcTestResult result = getNearby(params);

        // then
        assertNearbyInvalidInput(result, name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN", "campsite", "CAMPSITE,PARK", "CAMPSITE,,FOREST"})
    @DisplayName("[F-04] 반경 검색에 모르는 유형이나 빈 유형이 있으면 400 INVALID_INPUT을 응답하고 조회하지 않는다")
    void unknownTypeReturnsInvalidInputForNearby(String types) {
        // given
        Map<String, String> params = validNearbyParams();
        params.put("types", types);

        // when
        MvcTestResult result = getNearby(params);

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field =~ /types.*/)]")
                .asList()
                .isNotEmpty();
        verifyNoInteractions(spotNearbyQueryService);
    }

    @Test
    @DisplayName("[F-05] 로그인하지 않은 사용자가 박지 상세를 조회하면 박지 상세와 제보자를 받고, publicDetail은 null이며 아직 채우지 않는 항목은 빈 값이다")
    void anonymousReadsBakjiDetail() {
        // given
        SpotBakjiDetail bakji = new SpotBakjiDetail("능선 끝 평지", false, true, "WEAK", 12L, 31L, "새벽능선");
        when(spotDetailQueryService.findDetail(101L))
                .thenReturn(new SpotDetail(
                        101L,
                        SpotType.BAKJI,
                        "능선 끝 평지",
                        37.71,
                        128.75,
                        "강원 평창군",
                        WARNED,
                        bakji,
                        null,
                        NO_RATING,
                        List.of(),
                        List.of(),
                        List.of(),
                        null));

        // when
        MvcTestResult result = mvc.get().uri(PATH + "/101").exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {
                  "spotId": 101, "type": "BAKJI", "name": "능선 끝 평지", "lat": 37.71, "lng": 128.75,
                  "address": "강원 평창군",
                  "bakji": {
                    "description": "능선 끝 평지", "hasWater": false, "hasToilet": true, "signalLevel": "WEAK",
                    "confirmationCount": 12, "reporter": { "memberId": 31, "nickname": "새벽능선" }
                  },
                  "publicDetail": null,
                  "parkWarning": {
                    "warned": true, "areaName": "설악산 국립공원", "source": "KDPA", "sourceDate": "2026-01-01",
                    "notice": "참고용 데이터입니다. 공식 경계는 고시 도면을 확인하세요.",
                    "guide": "자연공원 안에서는 지정된 야영장 밖의 야영과 취사가 금지돼 있고, 어기면 과태료 대상입니다. 이곳에서 야영하지 말고 가까운 지정 야영장을 이용하세요."
                  },
                  "rating": { "average": null, "count": 0 },
                  "recentReviews": [], "expectedPeople": [], "recruitingBasecamps": [], "weather": null
                }
                """);
    }

    @Test
    @DisplayName("[F-10] 후기가 있는 장소의 상세는 평균 평점, 후기 수, 최근 후기를 후기 목록 항목과 같은 모양으로 응답한다")
    void detailShowsRatingAndRecentReviews() {
        // given
        SpotRecentReview review = new SpotRecentReview(
                9L, 31L, "새벽능선", LocalDate.of(2026, 10, 4), 5, "별이 좋았다", Instant.parse("2026-10-05T03:00:00Z"));
        when(spotDetailQueryService.findDetail(106L))
                .thenReturn(new SpotDetail(
                        106L,
                        SpotType.CAMPSITE,
                        "숲속 야영장",
                        37.52,
                        127.81,
                        null,
                        NOT_WARNED,
                        null,
                        null,
                        new SpotRating(4.3, 3),
                        List.of(review),
                        List.of(),
                        List.of(),
                        null));

        // when
        MvcTestResult result = mvc.get().uri(PATH + "/106").exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.rating").isEqualTo(Map.of("average", 4.3, "count", 3));
        assertThat(result).bodyJson().extractingPath("$.recentReviews").asList().hasSize(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.recentReviews[0]")
                .isEqualTo(Map.of(
                        "reviewId",
                        9,
                        "author",
                        Map.of("memberId", 31, "nickname", "새벽능선"),
                        "visitedDate",
                        "2026-10-04",
                        "rating",
                        5,
                        "content",
                        "별이 좋았다",
                        "createdAt",
                        "2026-10-05T03:00:00Z"));
    }

    @Test
    @DisplayName("[F-05] 야영장 상세를 조회하면 원천 정보와 시설, 휴장 여부를 받고, bakji는 null이다")
    void readsCampsiteDetail() {
        // given
        SpotFacilities facilities = new SpotFacilities("4", "2", "3", "개별", "전기,온수", null, "산책로", "계곡", "불가능");
        SpotPublicDetail publicDetail = new SpotPublicDetail(
                PublicSpotSource.GOCAMPING,
                "일반야영장,자동차야영장",
                facilities,
                "033-000-0000",
                "https://camp.example.com",
                LocalDate.of(2025, 12, 31),
                PublicSpotOperatingStatus.OPERATING,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
                true);
        when(spotDetailQueryService.findDetail(102L))
                .thenReturn(new SpotDetail(
                        102L,
                        SpotType.CAMPSITE,
                        "숲속 야영장",
                        37.52,
                        127.81,
                        null,
                        NOT_WARNED,
                        null,
                        publicDetail,
                        NO_RATING,
                        List.of(),
                        List.of(),
                        List.of(),
                        null));

        // when
        MvcTestResult result = mvc.get().uri(PATH + "/102").exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {
                  "spotId": 102, "type": "CAMPSITE", "name": "숲속 야영장", "lat": 37.52, "lng": 127.81,
                  "address": null,
                  "bakji": null,
                  "publicDetail": {
                    "source": "GOCAMPING", "category": "일반야영장,자동차야영장",
                    "facilities": {
                      "toiletCount": "4", "showerCount": "2", "sinkCount": "3", "brazier": "개별",
                      "amenities": "전기,온수", "amenitiesEtc": null, "nearbyFacilities": "산책로",
                      "nearbyFacilitiesEtc": "계곡", "petPolicy": "불가능"
                    },
                    "phone": "033-000-0000", "homepage": "https://camp.example.com", "sourceDate": "2025-12-31",
                    "operatingStatus": "OPERATING", "closedFrom": "2026-10-01", "closedUntil": "2026-10-31",
                    "closedNow": true
                  },
                  "parkWarning": { "warned": false },
                  "rating": { "average": null, "count": 0 },
                  "recentReviews": [], "expectedPeople": [], "recruitingBasecamps": [], "weather": null
                }
                """);
    }

    @Test
    @DisplayName("[F-05][F-09][SF-05] 경고 박지에 날씨가 있으면 경고 필드 전체와 단기·중기 예보, 출몰시각을 응답한다")
    void readsWarnedBakjiDetailWithWeather() {
        // given
        SpotBakjiDetail bakji = new SpotBakjiDetail("능선 끝 평지", false, true, "WEAK", 12L, 31L, "새벽능선");
        WeatherForecast forecast = new WeatherForecast(
                "기상청",
                List.of(new WeatherForecast.ShortTermForecast(Instant.parse("2026-10-04T05:00:00Z"), 18, 30, 2.5)),
                List.of(new WeatherForecast.MidTermForecast(LocalDate.of(2026, 10, 8), 10, 20, "맑음", "구름많음", 10, 20)));
        SunTimes sun =
                new SunTimes(LocalDate.of(2026, 10, 4), LocalTime.of(7, 1), LocalTime.of(18, 11), LocalTime.of(18, 38));
        when(spotDetailQueryService.findDetail(103L))
                .thenReturn(new SpotDetail(
                        103L,
                        SpotType.BAKJI,
                        "능선 끝 평지",
                        37.71,
                        128.75,
                        "강원 평창군",
                        WARNED,
                        bakji,
                        null,
                        NO_RATING,
                        List.of(),
                        List.of(
                                new SpotExpectedPeople(LocalDate.of(2026, 11, 7), 9),
                                new SpotExpectedPeople(LocalDate.of(2026, 11, 8), 4)),
                        List.of(
                                new SpotRecruitingBasecamp(
                                        41L,
                                        "설악 능선 1박",
                                        LocalDate.of(2026, 11, 7),
                                        LocalDate.of(2026, 11, 8),
                                        6,
                                        3,
                                        new SpotJoinCondition(1, 20, 30, true)),
                                new SpotRecruitingBasecamp(
                                        42L,
                                        "누구나 환영",
                                        LocalDate.of(2026, 11, 14),
                                        LocalDate.of(2026, 11, 16),
                                        4,
                                        1,
                                        new SpotJoinCondition(null, null, null, false))),
                        new SpotWeather(forecast, sun)));

        // when
        MvcTestResult result = mvc.get().uri(PATH + "/103").exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {
                  "spotId": 103, "type": "BAKJI", "name": "능선 끝 평지", "lat": 37.71, "lng": 128.75,
                  "address": "강원 평창군",
                  "bakji": {
                    "description": "능선 끝 평지", "hasWater": false, "hasToilet": true, "signalLevel": "WEAK",
                    "confirmationCount": 12, "reporter": { "memberId": 31, "nickname": "새벽능선" }
                  },
                  "publicDetail": null,
                  "parkWarning": {
                    "warned": true, "areaName": "설악산 국립공원", "source": "KDPA", "sourceDate": "2026-01-01",
                    "notice": "참고용 데이터입니다. 공식 경계는 고시 도면을 확인하세요.",
                    "guide": "자연공원 안에서는 지정된 야영장 밖의 야영과 취사가 금지돼 있고, 어기면 과태료 대상입니다. 이곳에서 야영하지 말고 가까운 지정 야영장을 이용하세요."
                  },
                  "rating": { "average": null, "count": 0 },
                  "recentReviews": [],
                  "expectedPeople": [{ "date": "2026-11-07", "count": 9 }, { "date": "2026-11-08", "count": 4 }],
                  "recruitingBasecamps": [
                    { "basecampId": 41, "title": "설악 능선 1박", "startDate": "2026-11-07", "endDate": "2026-11-08",
                      "capacity": 6, "headcount": 3,
                      "joinCondition": { "minTrustLevel": 1, "ageGroupMin": 20, "ageGroupMax": 30,
                                         "sameGenderOnly": true } },
                    { "basecampId": 42, "title": "누구나 환영", "startDate": "2026-11-14", "endDate": "2026-11-16",
                      "capacity": 4, "headcount": 1,
                      "joinCondition": { "minTrustLevel": null, "ageGroupMin": null, "ageGroupMax": null,
                                         "sameGenderOnly": false } }
                  ],
                  "weather": {
                    "source": "기상청",
                    "shortTerm": [
                      { "at": "2026-10-04T05:00:00Z", "temperature": 18, "precipitationProbability": 30,
                        "windSpeed": 2.5 }
                    ],
                    "midTerm": [
                      { "date": "2026-10-08", "minTemperature": 10, "maxTemperature": 20, "amSky": "맑음",
                        "pmSky": "구름많음", "amPrecipitationProbability": 10, "pmPrecipitationProbability": 20 }
                    ],
                    "sun": { "date": "2026-10-04", "sunrise": "07:01", "sunset": "18:11", "civilTwilightEnd": "18:38" }
                  }
                }
                """);
    }

    @Test
    @DisplayName("[F-05][F-09] 천문연만 실패해 sun이 없으면 weather 안에 sun 필드를 null로 남긴다")
    void keepsSunFieldAsNullWhenSunMissing() {
        // given
        WeatherForecast forecast = new WeatherForecast("기상청", List.of(), List.of());
        when(spotDetailQueryService.findDetail(104L))
                .thenReturn(new SpotDetail(
                        104L,
                        SpotType.CAMPSITE,
                        "숲속 야영장",
                        37.52,
                        127.81,
                        null,
                        NOT_WARNED,
                        null,
                        null,
                        NO_RATING,
                        List.of(),
                        List.of(),
                        List.of(),
                        new SpotWeather(forecast, null)));

        // when
        MvcTestResult result = mvc.get().uri(PATH + "/104").exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.weather.source").isEqualTo("기상청");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.weather.shortTerm")
                .asList()
                .isEmpty();
        assertThat(result).bodyJson().extractingPath("$.weather").asMap().containsKey("sun");
        assertThat(result).bodyJson().extractingPath("$.weather.sun").isNull();
    }

    @Test
    @DisplayName("[F-05] 경고가 아닌 장소의 parkWarning은 warned false만 담고 다른 필드는 응답에서 뺀다")
    void notWarnedParkWarningHasOnlyWarnedField() {
        // given
        when(spotDetailQueryService.findDetail(105L))
                .thenReturn(new SpotDetail(
                        105L,
                        SpotType.CAMPSITE,
                        "숲속 야영장",
                        37.52,
                        127.81,
                        null,
                        NOT_WARNED,
                        null,
                        null,
                        NO_RATING,
                        List.of(),
                        List.of(),
                        List.of(),
                        null));

        // when
        MvcTestResult result = mvc.get().uri(PATH + "/105").exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.parkWarning").isEqualTo(Map.of("warned", false));
        assertThat(result).bodyJson().extractingPath("$.weather").isNull();
    }

    @Test
    @DisplayName("[F-05] 서비스가 장소를 찾지 못하면 404 NOT_FOUND를 응답한다")
    void missingSpotReturnsNotFound() {
        // given
        when(spotDetailQueryService.findDetail(999L)).thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));

        // when
        MvcTestResult result = mvc.get().uri(PATH + "/999").exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("[F-05] 장소 ID가 숫자가 아니면 400 INVALID_INPUT과 spotId 필드 오류를 응답한다")
    void nonNumericSpotIdReturnsInvalidInput() {
        // when
        MvcTestResult result = mvc.get().uri(PATH + "/abc").exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field=='spotId')]")
                .asList()
                .isNotEmpty();
        verifyNoInteractions(spotDetailQueryService);
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

    private void assertNearbyInvalidInput(MvcTestResult result, String field) {
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field=='" + field + "')]")
                .asList()
                .isNotEmpty();
        verifyNoInteractions(spotNearbyQueryService);
    }

    private void whenNearbyServiceReturnsEmpty() {
        when(spotNearbyQueryService.findNearby(any())).thenReturn(new SpotNearbyPage(List.of(), 0, 20, false));
    }

    private SpotNearbyQuery capturedNearbyQuery() {
        ArgumentCaptor<SpotNearbyQuery> captor = ArgumentCaptor.forClass(SpotNearbyQuery.class);
        verify(spotNearbyQueryService).findNearby(captor.capture());
        return captor.getValue();
    }

    private MvcTestResult getNearby(Map<String, String> params) {
        MockMvcRequestBuilder request = mvc.get().uri(NEARBY_PATH);
        params.forEach(request::param);
        return request.exchange();
    }

    private static Map<String, String> validNearbyParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("lat", "37.5");
        params.put("lng", "127");
        params.put("radiusKm", "10");
        return params;
    }
}
