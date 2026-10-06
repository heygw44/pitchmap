package com.pitchmap.spot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.spot.domain.SpotType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SpotMapQueryServiceIntegrationTest {

    // 설악산 근처 영역이다. 서울시청은 위도만 이 영역 범위 안이고, 태백은 경도만 이 영역 범위 안이다.
    // 그래서 위도와 경도 중 하나라도 뒤바뀌어 비교되면 두 장소 중 하나가 결과에 섞이거나 설악산이 빠진다.
    private static final Area SEORAK_AREA = new Area(37.5, 128.5, 38.0, 129.0);
    private static final Coordinate SEORAK = new Coordinate(37.71, 128.75);
    private static final Coordinate SEOUL_CITY_HALL = new Coordinate(37.5665, 126.978);
    private static final Coordinate TAEBAEK = new Coordinate(37.16, 128.98);

    // 장소를 많이 넣을 때 쓰는 영역이다. 칸 하나는 위도·경도 0.05도 폭이다.
    private static final Area BULK_AREA = new Area(36.0, 127.0, 37.0, 128.0);

    @Autowired
    private SpotMapQueryService spotMapQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName("[F-03] 영역 안의 장소만 찾고, 저장한 위도·경도를 그대로 돌려준다")
    void findsOnlySpotsInsideAreaWithSameLatitudeAndLongitude() {
        // given: 장소는 WKT(위도, 경도 순서)로 넣는다. 그래서 조회 SQL의 좌표 순서가 틀리면 결과가 달라진다.
        long seorakId = insertSpot("CAMPSITE", "설악 야영장", SEORAK);
        insertSpot("CAMPSITE", "서울시청 야영장", SEOUL_CITY_HALL);
        insertSpot("CAMPSITE", "태백 야영장", TAEBAEK);

        // when
        SpotAreaResult result = spotMapQueryService.findInArea(SEORAK_AREA.query());

        // then
        assertThat(result.clusters()).isEmpty();
        assertThat(result.markers()).hasSize(1);
        SpotMarker marker = result.markers().getFirst();
        assertThat(marker.spotId()).isEqualTo(seorakId);
        assertThat(marker.type()).isEqualTo(SpotType.CAMPSITE);
        assertThat(marker.name()).isEqualTo("설악 야영장");
        assertThat(marker.lat()).isCloseTo(SEORAK.lat(), within(1e-9));
        assertThat(marker.lng()).isCloseTo(SEORAK.lng(), within(1e-9));
        assertThat(marker.parkWarning()).isFalse();
        assertThat(marker.closedNow()).isFalse();
    }

    @Test
    @DisplayName("[F-03] 영역 경계선 위의 장소도 찾고, 경계선 바로 밖의 장소는 뺀다")
    void includesSpotsOnAreaEdgeAndExcludesSpotsJustOutside() {
        // given: 남쪽 변 한가운데는 측지선 다각형으로 판정하면 영역 밖으로 나가는 위치다.
        long southEdgeId = insertSpot("CAMPSITE", "남쪽 경계 야영장", new Coordinate(37.5, 128.75));
        long westEdgeId = insertSpot("CAMPSITE", "서쪽 경계 야영장", new Coordinate(37.75, 128.5));
        long northEastCornerId = insertSpot("CAMPSITE", "북동 꼭짓점 야영장", new Coordinate(38.0, 129.0));
        insertSpot("CAMPSITE", "남쪽 경계 밖 야영장", new Coordinate(37.4999, 128.75));
        insertSpot("CAMPSITE", "동쪽 경계 밖 야영장", new Coordinate(37.75, 129.0001));

        // when
        SpotAreaResult result = spotMapQueryService.findInArea(SEORAK_AREA.query());

        // then
        assertThat(result.markers())
                .extracting(SpotMarker::spotId)
                .containsExactly(southEdgeId, westEdgeId, northEastCornerId);
    }

    @Test
    @DisplayName("[F-03] 숨김, 검토 대기, 삭제 상태의 장소는 지도에 나오지 않는다")
    void excludesSpotsThatAreNotActive() {
        // given
        long activeId = insertSpot("CAMPSITE", "ACTIVE", false, SEORAK);
        insertSpot("CAMPSITE", "HIDDEN", false, SEORAK);
        insertSpot("BAKJI", "PENDING_REVIEW", false, SEORAK);
        insertSpot("CAMPSITE", "DELETED", false, SEORAK);

        // when
        SpotAreaResult result = spotMapQueryService.findInArea(SEORAK_AREA.query());

        // then
        assertThat(result.markers()).extracting(SpotMarker::spotId).containsExactly(activeId);
    }

    @Test
    @DisplayName("[F-03] 유형을 지정하면 그 유형의 장소만 찾고, 지정하지 않으면 모든 유형을 찾는다")
    void filtersByTypesOnlyWhenTypesAreGiven() {
        // given
        long campsiteId = insertSpot("CAMPSITE", "야영장", SEORAK);
        long forestId = insertSpot("FOREST", "휴양림", SEORAK);
        long bakjiId = insertSpot("BAKJI", "박지", SEORAK);

        // when
        SpotAreaResult filtered = spotMapQueryService.findInArea(
                SEORAK_AREA.query(Set.of(SpotType.FOREST, SpotType.BAKJI), false, false, false));
        SpotAreaResult all = spotMapQueryService.findInArea(SEORAK_AREA.query());

        // then
        assertThat(filtered.markers()).extracting(SpotMarker::spotId).containsExactly(forestId, bakjiId);
        assertThat(all.markers()).extracting(SpotMarker::spotId).containsExactly(campsiteId, forestId, bakjiId);
    }

    @Test
    @DisplayName("[F-03] 공원 경계 경고가 붙은 장소는 경고 표시와 함께 나오고, 경고 제외를 켜면 빠진다")
    void reportsParkWarningAndExcludesWarnedSpotsOnRequest() {
        // given
        long warnedId = insertSpot("BAKJI", "ACTIVE", true, SEORAK);
        long plainId = insertSpot("BAKJI", "ACTIVE", false, SEORAK);

        // when
        SpotAreaResult withWarned = spotMapQueryService.findInArea(SEORAK_AREA.query());
        SpotAreaResult withoutWarned = spotMapQueryService.findInArea(SEORAK_AREA.query(Set.of(), false, false, true));

        // then
        assertThat(withWarned.markers())
                .extracting(SpotMarker::spotId, SpotMarker::parkWarning)
                .containsExactly(tuple(warnedId, true), tuple(plainId, false));
        assertThat(withoutWarned.markers()).extracting(SpotMarker::spotId).containsExactly(plainId);
    }

    @Test
    @DisplayName("[F-03] 물·화장실 필터는 해당 시설이 있는 박지만 남기고, 야영장과 휴양림은 시설과 상관없이 남긴다")
    void facilityFiltersApplyOnlyToBakji() {
        // given
        long reporterId = insertMember();
        long campsiteId = insertSpot("CAMPSITE", "야영장", SEORAK);
        long forestId = insertSpot("FOREST", "휴양림", SEORAK);
        long waterOnlyId = insertBakji(reporterId, true, false);
        long toiletOnlyId = insertBakji(reporterId, false, true);
        long bothId = insertBakji(reporterId, true, true);
        long noneId = insertBakji(reporterId, false, false);

        // when
        SpotAreaResult water = spotMapQueryService.findInArea(SEORAK_AREA.query(Set.of(), true, false, false));
        SpotAreaResult toilet = spotMapQueryService.findInArea(SEORAK_AREA.query(Set.of(), false, true, false));
        SpotAreaResult both = spotMapQueryService.findInArea(SEORAK_AREA.query(Set.of(), true, true, false));
        SpotAreaResult none = spotMapQueryService.findInArea(SEORAK_AREA.query());

        // then
        assertThat(water.markers())
                .extracting(SpotMarker::spotId)
                .containsExactly(campsiteId, forestId, waterOnlyId, bothId);
        assertThat(toilet.markers())
                .extracting(SpotMarker::spotId)
                .containsExactly(campsiteId, forestId, toiletOnlyId, bothId);
        assertThat(both.markers()).extracting(SpotMarker::spotId).containsExactly(campsiteId, forestId, bothId);
        assertThat(none.markers())
                .extracting(SpotMarker::spotId)
                .containsExactly(campsiteId, forestId, waterOnlyId, toiletOnlyId, bothId, noneId);
    }

    @Test
    @DisplayName("[F-03] 휴장 여부는 한국 날짜로 계산하고, 휴장 중인 장소도 지도에서 숨기지 않는다")
    void computesClosedNowWithKoreanDateAndKeepsClosedSpots() {
        // given: 한국은 2026-11-01 0시 30분이고, UTC로는 아직 2026-10-31이다.
        clock.setInstant(Instant.parse("2026-10-31T15:30:00Z"));
        long reporterId = insertMember();
        long closedPeriodId = insertSpot("CAMPSITE", "휴장 기간 중 야영장", SEORAK);
        insertPublicDetail(
                closedPeriodId, "GOCAMPING", "OPERATING", LocalDate.parse("2026-11-01"), LocalDate.parse("2027-03-31"));
        long endedPeriodId = insertSpot("CAMPSITE", "휴장 기간 끝난 야영장", SEORAK);
        insertPublicDetail(
                endedPeriodId,
                "GOCAMPING",
                "TEMPORARILY_CLOSED",
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-03-31"));
        long closedStatusId = insertSpot("FOREST", "휴장 상태 휴양림", SEORAK);
        insertPublicDetail(closedStatusId, "FOREST", "TEMPORARILY_CLOSED", null, null);
        long operatingId = insertSpot("CAMPSITE", "운영 중 야영장", SEORAK);
        insertPublicDetail(operatingId, "GOCAMPING", "OPERATING", null, null);
        long bakjiId = insertBakji(reporterId, true, true);

        // when
        SpotAreaResult result = spotMapQueryService.findInArea(SEORAK_AREA.query());

        // then
        assertThat(result.markers())
                .extracting(SpotMarker::spotId, SpotMarker::closedNow)
                .containsExactly(
                        tuple(closedPeriodId, true),
                        tuple(endedPeriodId, false),
                        tuple(closedStatusId, true),
                        tuple(operatingId, false),
                        tuple(bakjiId, false));
    }

    @Test
    @DisplayName("[F-03] 영역 안 장소가 500개면 모두 마커로 주고 묶음은 비운다")
    void returnsAllMarkersWhenSpotCountIsAtLimit() {
        // given
        insertCampsites(spreadInBulkArea(SpotMapQueryService.MAX_ITEMS));

        // when
        SpotAreaResult result = spotMapQueryService.findInArea(BULK_AREA.query());

        // then
        assertThat(result.markers()).hasSize(SpotMapQueryService.MAX_ITEMS);
        assertThat(result.markers()).extracting(SpotMarker::spotId).isSorted();
        assertThat(result.clusters()).isEmpty();
    }

    @Test
    @DisplayName("[F-03] 영역 안 장소가 500개를 넘으면 마커를 비우고 400칸 이하의 묶음으로 모든 장소를 센다")
    void returnsClustersWhenSpotCountExceedsLimit() {
        // given
        int spotCount = SpotMapQueryService.MAX_ITEMS + 1;
        insertCampsites(spreadInBulkArea(spotCount));

        // when
        SpotAreaResult result = spotMapQueryService.findInArea(BULK_AREA.query());

        // then
        assertThat(result.markers()).isEmpty();
        assertThat(result.clusters())
                .isNotEmpty()
                .hasSizeLessThanOrEqualTo(SpotMapQueryService.GRID_SIZE * SpotMapQueryService.GRID_SIZE);
        assertThat(result.clusters().stream().mapToLong(SpotCluster::count).sum())
                .isEqualTo(spotCount);
        assertThat(result.clusters()).allSatisfy(cluster -> {
            assertThat(cluster.lat()).isBetween(BULK_AREA.swLat(), BULK_AREA.neLat());
            assertThat(cluster.lng()).isBetween(BULK_AREA.swLng(), BULK_AREA.neLng());
        });
    }

    @Test
    @DisplayName("[F-03] 묶음은 칸 안 장소의 평균 좌표와 수를 남쪽 행부터 주고, 북쪽·동쪽 경계선 위의 장소는 마지막 칸에 넣는다")
    void clustersByGridCellInRowOrderWithEdgeSpotsInLastCell() {
        // given: 칸 하나는 0.05도 폭이다. 남서 칸에는 두 좌표를 섞어 넣고, 동쪽 경계선과 북쪽 경계선 위에도 장소를 둔다.
        List<Coordinate> coordinates = new ArrayList<>();
        coordinates.addAll(Collections.nCopies(100, new Coordinate(36.01, 127.01)));
        coordinates.addAll(Collections.nCopies(100, new Coordinate(36.03, 127.03)));
        coordinates.addAll(Collections.nCopies(200, new Coordinate(36.02, 128.0)));
        coordinates.addAll(Collections.nCopies(101, new Coordinate(37.0, 127.02)));
        insertCampsites(coordinates);

        // when
        SpotAreaResult result = spotMapQueryService.findInArea(BULK_AREA.query());

        // then: 칸 번호를 열부터 정렬하면 북쪽 경계 묶음이 동쪽 경계 묶음보다 먼저 나온다.
        assertThat(result.markers()).isEmpty();
        assertThat(result.clusters()).hasSize(3);
        assertCluster(result.clusters().get(0), new Coordinate(36.02, 127.02), 200);
        assertCluster(result.clusters().get(1), new Coordinate(36.02, 128.0), 200);
        assertCluster(result.clusters().get(2), new Coordinate(37.0, 127.02), 101);
    }

    private static void assertCluster(SpotCluster cluster, Coordinate expectedCenter, long expectedCount) {
        assertThat(cluster.lat()).isCloseTo(expectedCenter.lat(), within(1e-9));
        assertThat(cluster.lng()).isCloseTo(expectedCenter.lng(), within(1e-9));
        assertThat(cluster.count()).isEqualTo(expectedCount);
    }

    // 25행 × 필요한 열 수만큼 0.04도 간격으로 늘어놓는다. 501개여도 열은 21개라서 모든 장소가 영역 안에 든다.
    private static List<Coordinate> spreadInBulkArea(int count) {
        List<Coordinate> coordinates = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            coordinates.add(new Coordinate(
                    BULK_AREA.swLat() + (i % 25) * 0.04 + 0.01, BULK_AREA.swLng() + (i / 25) * 0.04 + 0.01));
        }
        return coordinates;
    }

    private long insertSpot(String type, String name, Coordinate at) {
        jdbcTemplate.update(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                        + " VALUES (?, ?, ST_GeomFromText(?, 4326), 60, 127, 'ACTIVE', NOW(6), NOW(6))",
                type,
                name,
                at.wkt());
        return findSpotId(name);
    }

    private long insertSpot(String type, String status, boolean parkWarning, Coordinate at) {
        String name = TestSequence.unique(status);
        jdbcTemplate.update(
                "INSERT INTO spot"
                        + " (type, name, location, weather_nx, weather_ny, park_warning, status, created_at, updated_at)"
                        + " VALUES (?, ?, ST_GeomFromText(?, 4326), 60, 127, ?, ?, NOW(6), NOW(6))",
                type,
                name,
                at.wkt(),
                parkWarning,
                status);
        return findSpotId(name);
    }

    private long insertBakji(long reporterId, boolean hasWater, boolean hasToilet) {
        long spotId = insertSpot("BAKJI", TestSequence.unique("박지"), SEORAK);
        jdbcTemplate.update(
                "INSERT INTO bakji_detail (spot_id, reporter_id, has_water, has_toilet, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, NOW(6), NOW(6))",
                spotId,
                reporterId,
                hasWater,
                hasToilet);
        return spotId;
    }

    private void insertPublicDetail(
            long spotId, String source, String operatingStatus, LocalDate closedFrom, LocalDate closedUntil) {
        jdbcTemplate.update(
                "INSERT INTO public_spot_detail"
                        + " (spot_id, source, external_id, operating_status, closed_from, closed_until,"
                        + " synced_at, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, NOW(6), NOW(6), NOW(6))",
                spotId,
                source,
                String.valueOf(spotId),
                operatingStatus,
                closedFrom,
                closedUntil);
    }

    // 장소 수백 개를 한 문장으로 넣는다. 값은 테스트가 만든 숫자와 순번 이름뿐이라 SQL에 바로 넣는다.
    private void insertCampsites(List<Coordinate> coordinates) {
        String values = coordinates.stream()
                .map(at -> String.format(
                        Locale.ROOT,
                        "('CAMPSITE', '%s', ST_GeomFromText('%s', 4326), 60, 127, 'ACTIVE', NOW(6), NOW(6))",
                        TestSequence.unique("bulk"),
                        at.wkt()))
                .collect(Collectors.joining(", "));
        jdbcTemplate.update(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at) VALUES "
                        + values);
    }

    private long insertMember() {
        String nickname = TestSequence.nickname();
        jdbcTemplate.update(
                "INSERT INTO member (email, nickname, status, role, created_at, updated_at)"
                        + " VALUES (?, ?, 'ACTIVE', 'USER', NOW(6), NOW(6))",
                TestSequence.email(),
                nickname);
        return jdbcTemplate.queryForObject("SELECT id FROM member WHERE nickname = ?", Long.class, nickname);
    }

    private long findSpotId(String name) {
        return jdbcTemplate.queryForObject("SELECT id FROM spot WHERE name = ?", Long.class, name);
    }

    private record Coordinate(double lat, double lng) {

        // SRID 4326 좌표의 WKT는 위도, 경도 순서다.
        String wkt() {
            return String.format(Locale.ROOT, "POINT(%s %s)", lat, lng);
        }
    }

    private record Area(double swLat, double swLng, double neLat, double neLng) {

        SpotAreaQuery query() {
            return query(Set.of(), false, false, false);
        }

        SpotAreaQuery query(Set<SpotType> types, boolean hasWater, boolean hasToilet, boolean excludeWarning) {
            return new SpotAreaQuery(swLat, swLng, neLat, neLng, types, hasWater, hasToilet, excludeWarning);
        }
    }
}
