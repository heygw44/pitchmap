package com.pitchmap.spot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.spot.domain.SpotErrorCode;
import com.pitchmap.spot.domain.SpotType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SpotNearbyQueryServiceIntegrationTest {

    // 중심은 서울시청이다. 아래 장소의 거리는 MySQL이 쓰는 반지름 6,370,986m 구 위에서 하버사인 공식으로 미리 계산했다.
    private static final Coordinate CENTER = new Coordinate(37.5665, 126.978);
    private static final double RADIUS_KM = 10;

    private static final Coordinate NORTH_2_KM = new Coordinate(37.5845, 126.978); // 2.0015km
    private static final Coordinate EAST_5_KM = new Coordinate(37.5665, 127.0347); // 4.9974km
    private static final Coordinate SOUTH_8_KM = new Coordinate(37.4946, 126.978); // 7.9949km
    private static final Coordinate NORTH_12_KM = new Coordinate(37.6744, 126.978); // 11.9979km

    // 반경 10km 경계 근처다. 동서 방향은 위도에 따라 1도의 거리가 줄어서, 서비스가 후보 사각형의 경도 폭을 좁게 잡으면 먼저 빠진다.
    private static final Coordinate NORTH_9_8_KM = new Coordinate(37.6546, 126.978); // 9.7963km
    private static final Coordinate SOUTH_9_8_KM = new Coordinate(37.4784, 126.978); // 9.7963km
    private static final Coordinate EAST_9_8_KM = new Coordinate(37.5665, 127.0891); // 9.7921km
    private static final Coordinate WEST_9_8_KM = new Coordinate(37.5665, 126.8669); // 9.7921km
    private static final Coordinate EAST_10_2_KM = new Coordinate(37.5665, 127.0936); // 10.1888km

    @Autowired
    private SpotNearbyQueryService spotNearbyQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName("[F-04] 반경 안의 장소만 가까운 순서로 찾고, 거리를 km로 함께 돌려준다")
    void findsOnlySpotsInsideRadiusOrderedByDistance() {
        // given: 장소는 WKT(위도, 경도 순서)로 넣고, 가까운 순서와 다르게 넣는다.
        long southId = insertSpot("CAMPSITE", "남쪽 8km 야영장", SOUTH_8_KM);
        long northId = insertSpot("FOREST", "북쪽 2km 휴양림", NORTH_2_KM);
        insertSpot("CAMPSITE", "북쪽 12km 야영장", NORTH_12_KM);
        long eastId = insertSpot("CAMPSITE", "동쪽 5km 야영장", EAST_5_KM);

        // when
        SpotNearbyPage result = spotNearbyQueryService.findNearby(query(CENTER, RADIUS_KM));

        // then
        assertThat(result.content()).extracting(SpotNearbyItem::spotId).containsExactly(northId, eastId, southId);
        assertThat(result.content())
                .extracting(SpotNearbyItem::distanceKm)
                .satisfiesExactly(
                        km -> assertThat(km).isCloseTo(2.0015, within(0.01)),
                        km -> assertThat(km).isCloseTo(4.9974, within(0.01)),
                        km -> assertThat(km).isCloseTo(7.9949, within(0.01)));
        SpotNearbyItem nearest = result.content().getFirst();
        assertThat(nearest.type()).isEqualTo(SpotType.FOREST);
        assertThat(nearest.name()).isEqualTo("북쪽 2km 휴양림");
        assertThat(nearest.lat()).isCloseTo(NORTH_2_KM.lat(), within(1e-9));
        assertThat(nearest.lng()).isCloseTo(NORTH_2_KM.lng(), within(1e-9));
        assertThat(nearest.parkWarning()).isFalse();
        assertThat(nearest.closedNow()).isFalse();
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("[F-04] 동서남북 반경 경계 근처의 장소는 모두 찾고, 경계 바로 밖의 장소는 뺀다")
    void includesSpotsNearRadiusInEveryDirectionAndExcludesSpotJustOutside() {
        // given
        long northId = insertSpot("CAMPSITE", "북쪽 경계 근처", NORTH_9_8_KM);
        long southId = insertSpot("CAMPSITE", "남쪽 경계 근처", SOUTH_9_8_KM);
        long eastId = insertSpot("CAMPSITE", "동쪽 경계 근처", EAST_9_8_KM);
        long westId = insertSpot("CAMPSITE", "서쪽 경계 근처", WEST_9_8_KM);
        insertSpot("CAMPSITE", "동쪽 경계 밖", EAST_10_2_KM);

        // when
        SpotNearbyPage result = spotNearbyQueryService.findNearby(query(CENTER, RADIUS_KM));

        // then: 동서 두 곳과 남북 두 곳은 거리가 거의 같아서 순서는 보지 않는다.
        assertThat(result.content())
                .extracting(SpotNearbyItem::spotId)
                .containsExactlyInAnyOrder(northId, southId, eastId, westId);
    }

    @Test
    @DisplayName("[F-04] 중심과 장소의 위도·경도를 서로 바꿔 읽지 않는다")
    void doesNotSwapLatitudeAndLongitude() {
        // given: 위도와 경도가 비슷한 곳에서 두 값을 맞바꾼 두 장소를 둔다. 두 장소는 서로 약 14km 떨어져 있다.
        // 그래서 조회 SQL이 좌표 순서를 바꿔 읽으면 중심이 다른 장소로 옮겨 가서 결과가 달라진다.
        Coordinate center = new Coordinate(37.5, 37.6);
        long centerSpotId = insertSpot("CAMPSITE", "중심 야영장", center);
        insertSpot("CAMPSITE", "좌표를 맞바꾼 야영장", new Coordinate(37.6, 37.5));

        // when
        SpotNearbyPage result = spotNearbyQueryService.findNearby(query(center, 5));

        // then
        assertThat(result.content()).extracting(SpotNearbyItem::spotId).containsExactly(centerSpotId);
        assertThat(result.content().getFirst().lat()).isCloseTo(37.5, within(1e-9));
        assertThat(result.content().getFirst().lng()).isCloseTo(37.6, within(1e-9));
        assertThat(result.content().getFirst().distanceKm()).isZero();
    }

    @Test
    @DisplayName("[F-04] 숨김, 검토 대기, 삭제 상태의 장소는 반경 검색에 나오지 않는다")
    void excludesSpotsThatAreNotActive() {
        // given
        long activeId = insertSpot("CAMPSITE", "ACTIVE", false, NORTH_2_KM);
        insertSpot("CAMPSITE", "HIDDEN", false, NORTH_2_KM);
        insertSpot("BAKJI", "PENDING_REVIEW", false, NORTH_2_KM);
        insertSpot("CAMPSITE", "DELETED", false, NORTH_2_KM);

        // when
        SpotNearbyPage result = spotNearbyQueryService.findNearby(query(CENTER, RADIUS_KM));

        // then
        assertThat(result.content()).extracting(SpotNearbyItem::spotId).containsExactly(activeId);
    }

    @Test
    @DisplayName("[F-04] 유형을 지정하면 그 유형의 장소만 찾고, 지정하지 않으면 모든 유형을 찾는다")
    void filtersByTypesOnlyWhenTypesAreGiven() {
        // given
        long campsiteId = insertSpot("CAMPSITE", "야영장", NORTH_2_KM);
        long forestId = insertSpot("FOREST", "휴양림", EAST_5_KM);
        long bakjiId = insertSpot("BAKJI", "박지", SOUTH_8_KM);

        // when
        SpotNearbyPage filtered = spotNearbyQueryService.findNearby(
                query(CENTER, RADIUS_KM, Set.of(SpotType.FOREST, SpotType.BAKJI), false, false, false, 0, 20));
        SpotNearbyPage all = spotNearbyQueryService.findNearby(query(CENTER, RADIUS_KM));

        // then
        assertThat(filtered.content()).extracting(SpotNearbyItem::spotId).containsExactly(forestId, bakjiId);
        assertThat(all.content()).extracting(SpotNearbyItem::spotId).containsExactly(campsiteId, forestId, bakjiId);
    }

    @Test
    @DisplayName("[F-04] 공원 경계 경고가 붙은 장소는 경고 표시와 함께 나오고, 경고 제외를 켜면 빠진다")
    void reportsParkWarningAndExcludesWarnedSpotsOnRequest() {
        // given
        long warnedId = insertSpot("BAKJI", "ACTIVE", true, NORTH_2_KM);
        long plainId = insertSpot("BAKJI", "ACTIVE", false, EAST_5_KM);

        // when
        SpotNearbyPage withWarned = spotNearbyQueryService.findNearby(query(CENTER, RADIUS_KM));
        SpotNearbyPage withoutWarned =
                spotNearbyQueryService.findNearby(query(CENTER, RADIUS_KM, Set.of(), false, false, true, 0, 20));

        // then
        assertThat(withWarned.content())
                .extracting(SpotNearbyItem::spotId, SpotNearbyItem::parkWarning)
                .containsExactly(tuple(warnedId, true), tuple(plainId, false));
        assertThat(withoutWarned.content()).extracting(SpotNearbyItem::spotId).containsExactly(plainId);
    }

    @Test
    @DisplayName("[F-04] 물·화장실 필터는 해당 시설이 있는 박지만 남기고, 야영장과 휴양림은 시설과 상관없이 남긴다")
    void facilityFiltersApplyOnlyToBakji() {
        // given: 모두 같은 자리라 거리가 같으므로 결과는 ID 순서로 나온다.
        long reporterId = insertMember();
        long campsiteId = insertSpot("CAMPSITE", "야영장", NORTH_2_KM);
        long forestId = insertSpot("FOREST", "휴양림", NORTH_2_KM);
        long waterOnlyId = insertBakji(reporterId, true, false);
        long toiletOnlyId = insertBakji(reporterId, false, true);
        long bothId = insertBakji(reporterId, true, true);
        long noneId = insertBakji(reporterId, false, false);

        // when
        SpotNearbyPage water =
                spotNearbyQueryService.findNearby(query(CENTER, RADIUS_KM, Set.of(), true, false, false, 0, 20));
        SpotNearbyPage toilet =
                spotNearbyQueryService.findNearby(query(CENTER, RADIUS_KM, Set.of(), false, true, false, 0, 20));
        SpotNearbyPage both =
                spotNearbyQueryService.findNearby(query(CENTER, RADIUS_KM, Set.of(), true, true, false, 0, 20));
        SpotNearbyPage none = spotNearbyQueryService.findNearby(query(CENTER, RADIUS_KM));

        // then
        assertThat(water.content())
                .extracting(SpotNearbyItem::spotId)
                .containsExactly(campsiteId, forestId, waterOnlyId, bothId);
        assertThat(toilet.content())
                .extracting(SpotNearbyItem::spotId)
                .containsExactly(campsiteId, forestId, toiletOnlyId, bothId);
        assertThat(both.content()).extracting(SpotNearbyItem::spotId).containsExactly(campsiteId, forestId, bothId);
        assertThat(none.content())
                .extracting(SpotNearbyItem::spotId)
                .containsExactly(campsiteId, forestId, waterOnlyId, toiletOnlyId, bothId, noneId);
    }

    @Test
    @DisplayName("[F-04] 휴장 중인 야영장도 반경 검색에서 빼지 않고 휴장으로 표시하며, 박지는 휴장으로 보지 않는다")
    void keepsClosedSpotsAndMarksThemClosed() {
        // given: 한국은 2026-11-01 0시 30분이고, UTC로는 아직 2026-10-31이다.
        clock.setInstant(Instant.parse("2026-10-31T15:30:00Z"));
        long reporterId = insertMember();
        long closedId = insertSpot("CAMPSITE", "휴장 기간 중 야영장", NORTH_2_KM);
        insertPublicDetail(
                closedId, "GOCAMPING", "OPERATING", LocalDate.parse("2026-11-01"), LocalDate.parse("2027-03-31"));
        long operatingId = insertSpot("FOREST", "운영 중 휴양림", NORTH_2_KM);
        insertPublicDetail(operatingId, "FOREST", "OPERATING", null, null);
        long bakjiId = insertBakji(reporterId, true, true);

        // when
        SpotNearbyPage result = spotNearbyQueryService.findNearby(query(CENTER, RADIUS_KM));

        // then
        assertThat(result.content())
                .extracting(SpotNearbyItem::spotId, SpotNearbyItem::closedNow)
                .containsExactly(tuple(closedId, true), tuple(operatingId, false), tuple(bakjiId, false));
    }

    @Test
    @DisplayName("[F-04] 페이지 크기 2로 장소 3곳을 나누면 첫 페이지에 가까운 두 곳, 다음 페이지에 먼 한 곳이 나오고, 거리가 같으면 ID 순서다")
    void pagesByDistanceThenId() {
        // given: 먼 장소를 먼저 넣어 ID가 가장 작게 하고, 가까운 두 장소는 같은 자리에 둔다.
        long farId = insertSpot("CAMPSITE", "먼 야영장", SOUTH_8_KM);
        long firstNearId = insertSpot("CAMPSITE", "가까운 야영장 1", NORTH_2_KM);
        long secondNearId = insertSpot("CAMPSITE", "가까운 야영장 2", NORTH_2_KM);

        // when
        SpotNearbyPage first =
                spotNearbyQueryService.findNearby(query(CENTER, RADIUS_KM, Set.of(), false, false, false, 0, 2));
        SpotNearbyPage second =
                spotNearbyQueryService.findNearby(query(CENTER, RADIUS_KM, Set.of(), false, false, false, 1, 2));

        // then
        assertThat(first.content()).extracting(SpotNearbyItem::spotId).containsExactly(firstNearId, secondNearId);
        assertThat(first.page()).isZero();
        assertThat(first.size()).isEqualTo(2);
        assertThat(first.hasNext()).isTrue();
        assertThat(second.content()).extracting(SpotNearbyItem::spotId).containsExactly(farId);
        assertThat(second.page()).isEqualTo(1);
        assertThat(second.hasNext()).isFalse();
    }

    @Test
    @DisplayName("[F-04] 반경 50km는 허용해서 49km 떨어진 장소를 찾는다")
    void allowsMaximumRadius() {
        // given
        long farId = insertSpot("CAMPSITE", "북쪽 49km 야영장", new Coordinate(38.0072, 126.978));

        // when
        SpotNearbyPage result = spotNearbyQueryService.findNearby(query(CENTER, SpotNearbyQuery.MAX_RADIUS_KM));

        // then
        assertThat(result.content())
                .extracting(SpotNearbyItem::spotId, SpotNearbyItem::distanceKm)
                .containsExactly(tuple(farId, 49.0));
    }

    @Test
    @DisplayName("[F-04] 반경이 50km를 넘으면 검색 조건을 만들 때 SPOT_RADIUS_TOO_LARGE로 거부한다")
    void rejectsRadiusOverMaximum() {
        assertThatThrownBy(() -> query(CENTER, 50.1))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode()).isEqualTo(SpotErrorCode.SPOT_RADIUS_TOO_LARGE));
    }

    private static SpotNearbyQuery query(Coordinate center, double radiusKm) {
        return query(center, radiusKm, Set.of(), false, false, false, 0, 20);
    }

    private static SpotNearbyQuery query(
            Coordinate center,
            double radiusKm,
            Set<SpotType> types,
            boolean hasWater,
            boolean hasToilet,
            boolean excludeWarning,
            int page,
            int size) {
        return new SpotNearbyQuery(
                center.lat(), center.lng(), radiusKm, types, hasWater, hasToilet, excludeWarning, page, size);
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
        long spotId = insertSpot("BAKJI", TestSequence.unique("박지"), NORTH_2_KM);
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
}
