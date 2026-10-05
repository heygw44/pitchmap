package com.pitchmap.spot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.spot.domain.GeoPoint;
import com.pitchmap.spot.domain.WeatherGrid;
import com.pitchmap.spot.infra.SpotLocationProbeRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class PublicSpotSyncServiceIntegrationTest {

    // 서울시청 좌표다. 위도와 경도가 뒤바뀌면 위도 자리에 126.978이 들어가서 MySQL이 값을 거부하거나 읽은 좌표가 달라진다.
    private static final double SEOUL_LATITUDE = 37.5665;
    private static final double SEOUL_LONGITUDE = 126.978;
    // 강원도 춘천 근처 좌표다. 서울시청과 기상청 격자가 다르다.
    private static final double CHUNCHEON_LATITUDE = 37.8813;
    private static final double CHUNCHEON_LONGITUDE = 127.7298;
    private static final Duration NEXT_SYNC = Duration.ofDays(1);
    private static final TypeReference<Map<String, String>> FACILITIES_TYPE = new TypeReference<>() {};

    @Autowired
    private PublicSpotSyncService publicSpotSyncService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private SpotLocationProbeRepository spotLocationProbeRepository;

    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName("[F-06] 처음 적재하면 고캠핑 야영장을 ACTIVE 상태의 장소와 공공데이터 상세로 저장한다")
    void firstUpsertInsertsSpotAndDetail() {
        // given
        Map<String, String> facilities = Map.of("sbrsCl", "전기,무선인터넷", "toiletCo", "4", "animalCmgCl", "불가능");
        PublicSpotCommand command = new PublicSpotCommand(
                "100",
                "솔숲 야영장",
                CHUNCHEON_LATITUDE,
                CHUNCHEON_LONGITUDE,
                "강원 춘천시 남산면 1",
                "일반야영장",
                facilities,
                "033-000-0000",
                "https://example.com",
                null,
                null,
                null);

        // when
        PublicSpotUpsertResult result = publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(command));

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(1, 0, 0, 0));
        WeatherGrid expectedGrid = WeatherGrid.from(new GeoPoint(CHUNCHEON_LATITUDE, CHUNCHEON_LONGITUDE));
        StoredSpot spot = findSpot(PublicSpotSource.GOCAMPING, "100");
        assertThat(spot.type()).isEqualTo("CAMPSITE");
        assertThat(spot.status()).isEqualTo("ACTIVE");
        assertThat(spot.name()).isEqualTo("솔숲 야영장");
        assertThat(spot.address()).isEqualTo("강원 춘천시 남산면 1");
        assertThat(new WeatherGrid(spot.weatherNx(), spot.weatherNy())).isEqualTo(expectedGrid);
        assertThat(spot.parkWarning()).isFalse();
        assertThat(spot.createdAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
        assertThat(spot.updatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
        assertThat(spot.category()).isEqualTo("일반야영장");
        assertThat(jsonMapper.readValue(spot.facilities(), FACILITIES_TYPE)).isEqualTo(facilities);
        assertThat(spot.phone()).isEqualTo("033-000-0000");
        assertThat(spot.homepage()).isEqualTo("https://example.com");
        assertThat(spot.syncedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
        assertThat(spot.detailUpdatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
    }

    @Test
    @DisplayName("[F-06] 적재한 좌표를 MySQL과 JPA가 모두 같은 위도·경도로 읽는다")
    void upsertedCoordinateIsReadBackWithSameLatitudeAndLongitude() {
        // given
        PublicSpotCommand command = campsite("100", "서울시청 야영장", SEOUL_LATITUDE, SEOUL_LONGITUDE);

        // when
        publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(command));

        // then
        long spotId = findSpotId(PublicSpotSource.GOCAMPING, "100");
        Map<String, Object> stored = jdbcTemplate.queryForMap(
                "SELECT ST_Latitude(location) AS latitude, ST_Longitude(location) AS longitude,"
                        + " ST_SRID(location) AS srid, ST_AsText(location) AS wkt FROM spot WHERE id = ?",
                spotId);
        assertThat((Double) stored.get("latitude")).isCloseTo(SEOUL_LATITUDE, within(1e-9));
        assertThat((Double) stored.get("longitude")).isCloseTo(SEOUL_LONGITUDE, within(1e-9));
        assertThat(((Number) stored.get("srid")).intValue()).isEqualTo(4326);
        // SRID 4326 좌표의 WKT는 위도, 경도 순서다.
        assertThat(stored.get("wkt")).isEqualTo("POINT(37.5665 126.978)");
        assertThat(spotLocationProbeRepository.findById(spotId).orElseThrow().getLocation())
                .isEqualTo(new GeoPoint(SEOUL_LATITUDE, SEOUL_LONGITUDE));
    }

    @Test
    @DisplayName("[F-06] 같은 데이터를 두 번 적재하면 행이 늘지 않고 수정 시각과 동기화 시각도 바뀌지 않는다")
    void upsertingSameBatchTwiceLeavesRowsUntouched() {
        // given: 키가 여러 개인 시설 정보를 넣는다. MySQL은 JSON 키 순서와 공백을 바꿔 저장하므로, 문자열로 비교하면 바뀐 것으로 잘못 본다.
        List<PublicSpotCommand> batch = List.of(
                campsite("100", "솔숲 야영장", SEOUL_LATITUDE, SEOUL_LONGITUDE),
                campsite("101", "갈대 야영장", CHUNCHEON_LATITUDE, CHUNCHEON_LONGITUDE));
        publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, batch);
        clock.advance(NEXT_SYNC);

        // when
        PublicSpotUpsertResult result = publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, batch);

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(0, 0, 2, 0));
        assertThat(count("spot")).isEqualTo(2);
        assertThat(count("public_spot_detail")).isEqualTo(2);
        for (String externalId : List.of("100", "101")) {
            StoredSpot spot = findSpot(PublicSpotSource.GOCAMPING, externalId);
            assertThat(spot.updatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
            assertThat(spot.detailUpdatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
            assertThat(spot.syncedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
        }
    }

    @Test
    @DisplayName("[F-06] 좌표가 바뀐 장소만 갱신하고 기상청 격자를 다시 계산한다")
    void updatesOnlyChangedSpotAndRecomputesWeatherGrid() {
        // given
        PublicSpotCommand unchanged = campsite("100", "솔숲 야영장", SEOUL_LATITUDE, SEOUL_LONGITUDE);
        PublicSpotCommand moving = campsite("101", "갈대 야영장", SEOUL_LATITUDE, SEOUL_LONGITUDE);
        publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(unchanged, moving));
        clock.advance(NEXT_SYNC);
        PublicSpotCommand moved = campsite("101", "갈대 야영장", CHUNCHEON_LATITUDE, CHUNCHEON_LONGITUDE);

        // when
        PublicSpotUpsertResult result =
                publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(unchanged, moved));

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(0, 1, 1, 0));
        Instant syncTime = MutableClock.DEFAULT_INSTANT.plus(NEXT_SYNC);
        StoredSpot movedSpot = findSpot(PublicSpotSource.GOCAMPING, "101");
        assertThat(movedSpot.latitude()).isCloseTo(CHUNCHEON_LATITUDE, within(1e-9));
        assertThat(movedSpot.longitude()).isCloseTo(CHUNCHEON_LONGITUDE, within(1e-9));
        assertThat(new WeatherGrid(movedSpot.weatherNx(), movedSpot.weatherNy()))
                .isEqualTo(WeatherGrid.from(new GeoPoint(CHUNCHEON_LATITUDE, CHUNCHEON_LONGITUDE)));
        assertThat(movedSpot.createdAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
        assertThat(movedSpot.updatedAt()).isEqualTo(syncTime);
        assertThat(movedSpot.syncedAt()).isEqualTo(syncTime);
        StoredSpot untouchedSpot = findSpot(PublicSpotSource.GOCAMPING, "100");
        assertThat(untouchedSpot.updatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
        assertThat(untouchedSpot.syncedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
    }

    @Test
    @DisplayName("[F-06] 시설 정보만 바뀌면 상세와 동기화 시각만 갱신하고 장소의 수정 시각은 그대로 둔다")
    void detailOnlyChangeUpdatesDetailButNotSpot() {
        // given
        publicSpotSyncService.upsert(
                PublicSpotSource.GOCAMPING, List.of(campsite("100", "솔숲 야영장", SEOUL_LATITUDE, SEOUL_LONGITUDE)));
        clock.advance(NEXT_SYNC);
        Map<String, String> newFacilities = Map.of("sbrsCl", "전기,온수", "toiletCo", "6");
        PublicSpotCommand changed = new PublicSpotCommand(
                "100",
                "솔숲 야영장",
                SEOUL_LATITUDE,
                SEOUL_LONGITUDE,
                "서울 중구 세종대로 110",
                "일반야영장",
                newFacilities,
                "02-000-0000",
                "https://example.com",
                null,
                null,
                null);

        // when
        PublicSpotUpsertResult result = publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(changed));

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(0, 1, 0, 0));
        Instant syncTime = MutableClock.DEFAULT_INSTANT.plus(NEXT_SYNC);
        StoredSpot spot = findSpot(PublicSpotSource.GOCAMPING, "100");
        assertThat(jsonMapper.readValue(spot.facilities(), FACILITIES_TYPE)).isEqualTo(newFacilities);
        assertThat(spot.syncedAt()).isEqualTo(syncTime);
        assertThat(spot.detailUpdatedAt()).isEqualTo(syncTime);
        assertThat(spot.updatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
    }

    @Test
    @DisplayName("[F-06] 관리자가 숨긴 장소는 동기화로 이름이 바뀌어도 숨김 상태와 공원 경고를 유지한다")
    void updateKeepsHiddenStatusAndParkWarning() {
        // given
        publicSpotSyncService.upsert(
                PublicSpotSource.GOCAMPING, List.of(campsite("100", "솔숲 야영장", SEOUL_LATITUDE, SEOUL_LONGITUDE)));
        long spotId = findSpotId(PublicSpotSource.GOCAMPING, "100");
        jdbcTemplate.update(
                "UPDATE spot SET status = 'HIDDEN', park_warning = TRUE, area_checked_at = ? WHERE id = ?",
                Timestamp.from(MutableClock.DEFAULT_INSTANT),
                spotId);
        clock.advance(NEXT_SYNC);

        // when
        PublicSpotUpsertResult result = publicSpotSyncService.upsert(
                PublicSpotSource.GOCAMPING, List.of(campsite("100", "솔숲 야영장(새 이름)", SEOUL_LATITUDE, SEOUL_LONGITUDE)));

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(0, 1, 0, 0));
        StoredSpot spot = findSpot(PublicSpotSource.GOCAMPING, "100");
        assertThat(spot.name()).isEqualTo("솔숲 야영장(새 이름)");
        assertThat(spot.status()).isEqualTo("HIDDEN");
        assertThat(spot.parkWarning()).isTrue();
        Integer areaChecked = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM spot WHERE id = ? AND area_checked_at IS NOT NULL", Integer.class, spotId);
        assertThat(areaChecked).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-06] 기상청 격자 밖 좌표의 장소는 건너뛰고 나머지 장소는 저장한다")
    void skipsSpotOutsideWeatherGridAndStoresOthers() {
        // given
        List<PublicSpotCommand> batch =
                List.of(campsite("100", "솔숲 야영장", SEOUL_LATITUDE, SEOUL_LONGITUDE), campsite("101", "적도 야영장", 0, 0));

        // when
        PublicSpotUpsertResult result = publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, batch);

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(1, 0, 0, 1));
        assertThat(count("spot")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList("SELECT external_id FROM public_spot_detail", String.class))
                .containsExactly("100");
    }

    @Test
    @DisplayName("[F-06] 한 묶음에 같은 외부 ID가 두 번 있으면 마지막 값 하나만 저장한다")
    void keepsLastCommandWhenExternalIdRepeatsInBatch() {
        // given
        List<PublicSpotCommand> batch = List.of(
                campsite("100", "옛 이름 야영장", SEOUL_LATITUDE, SEOUL_LONGITUDE),
                campsite("100", "새 이름 야영장", SEOUL_LATITUDE, SEOUL_LONGITUDE));

        // when
        PublicSpotUpsertResult result = publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, batch);

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(1, 0, 0, 1));
        assertThat(count("spot")).isEqualTo(1);
        assertThat(findSpot(PublicSpotSource.GOCAMPING, "100").name()).isEqualTo("새 이름 야영장");
    }

    @Test
    @DisplayName("[F-06] 자연휴양림의 외부 ID가 고캠핑 야영장과 같아도 별도 장소로 저장한다")
    void sameExternalIdFromDifferentSourceCreatesSeparateSpot() {
        // given
        publicSpotSyncService.upsert(
                PublicSpotSource.GOCAMPING, List.of(campsite("100", "솔숲 야영장", SEOUL_LATITUDE, SEOUL_LONGITUDE)));

        // when
        PublicSpotUpsertResult result = publicSpotSyncService.upsert(
                PublicSpotSource.FOREST, List.of(campsite("100", "솔숲 휴양림", CHUNCHEON_LATITUDE, CHUNCHEON_LONGITUDE)));

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(1, 0, 0, 0));
        assertThat(count("spot")).isEqualTo(2);
        StoredSpot campsite = findSpot(PublicSpotSource.GOCAMPING, "100");
        StoredSpot forest = findSpot(PublicSpotSource.FOREST, "100");
        assertThat(campsite.type()).isEqualTo("CAMPSITE");
        assertThat(campsite.name()).isEqualTo("솔숲 야영장");
        assertThat(forest.type()).isEqualTo("FOREST");
        assertThat(forest.name()).isEqualTo("솔숲 휴양림");
    }

    @Test
    @DisplayName("[F-06] 빈 묶음을 적재하면 아무것도 저장하지 않고 모든 건수가 0이다")
    void emptyBatchStoresNothing() {
        // when
        PublicSpotUpsertResult result = publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of());

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(0, 0, 0, 0));
        assertThat(count("spot")).isZero();
    }

    @Test
    @DisplayName("[F-06] 운영 상태와 휴장 기간을 상세에 저장하고, 값이 없으면 NULL로 저장한다")
    void storesOperatingStatusAndClosedDates() {
        // given
        PublicSpotCommand closed = campsite(
                "100",
                "솔숲 야영장",
                SEOUL_LATITUDE,
                SEOUL_LONGITUDE,
                PublicSpotOperatingStatus.TEMPORARILY_CLOSED,
                LocalDate.parse("2026-11-16"),
                LocalDate.parse("2027-03-15"));
        PublicSpotCommand noStatus = seoulCampsite("101", "갈대 야영장");

        // when
        PublicSpotUpsertResult result =
                publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(closed, noStatus));

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(2, 0, 0, 0));
        StoredSpot stored = findSpot(PublicSpotSource.GOCAMPING, "100");
        assertThat(stored.operatingStatus()).isEqualTo("TEMPORARILY_CLOSED");
        assertThat(stored.closedFrom()).isEqualTo(LocalDate.parse("2026-11-16"));
        assertThat(stored.closedUntil()).isEqualTo(LocalDate.parse("2027-03-15"));
        assertThat(stored.sourceRemovedAt()).isNull();
        StoredSpot empty = findSpot(PublicSpotSource.GOCAMPING, "101");
        assertThat(empty.operatingStatus()).isNull();
        assertThat(empty.closedFrom()).isNull();
        assertThat(empty.closedUntil()).isNull();
    }

    @Test
    @DisplayName("[F-06] 운영 상태와 휴장 기간이 있는 데이터를 두 번 적재해도 수정 시각과 동기화 시각이 바뀌지 않는다")
    void upsertingSameStatusAndDatesTwiceLeavesRowsUntouched() {
        // given
        List<PublicSpotCommand> batch = List.of(campsite(
                "100",
                "솔숲 야영장",
                SEOUL_LATITUDE,
                SEOUL_LONGITUDE,
                PublicSpotOperatingStatus.TEMPORARILY_CLOSED,
                LocalDate.parse("2026-11-16"),
                LocalDate.parse("2027-03-15")));
        publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, batch);
        clock.advance(NEXT_SYNC);

        // when
        PublicSpotUpsertResult result = publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, batch);

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(0, 0, 1, 0));
        StoredSpot spot = findSpot(PublicSpotSource.GOCAMPING, "100");
        assertThat(spot.updatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
        assertThat(spot.detailUpdatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
        assertThat(spot.syncedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
    }

    @Test
    @DisplayName("[F-06] 운영 상태나 휴장 기간만 바뀌면 상세와 동기화 시각만 갱신하고 장소의 수정 시각은 그대로 둔다")
    void operatingStatusOnlyChangeUpdatesDetailButNotSpot() {
        // given
        publicSpotSyncService.upsert(
                PublicSpotSource.GOCAMPING,
                List.of(campsite(
                        "100",
                        "솔숲 야영장",
                        SEOUL_LATITUDE,
                        SEOUL_LONGITUDE,
                        PublicSpotOperatingStatus.OPERATING,
                        null,
                        null)));
        clock.advance(NEXT_SYNC);
        PublicSpotCommand changed = campsite(
                "100",
                "솔숲 야영장",
                SEOUL_LATITUDE,
                SEOUL_LONGITUDE,
                PublicSpotOperatingStatus.TEMPORARILY_CLOSED,
                LocalDate.parse("2026-12-01"),
                LocalDate.parse("2027-02-28"));

        // when
        PublicSpotUpsertResult result = publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(changed));

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(0, 1, 0, 0));
        Instant syncTime = MutableClock.DEFAULT_INSTANT.plus(NEXT_SYNC);
        StoredSpot spot = findSpot(PublicSpotSource.GOCAMPING, "100");
        assertThat(spot.operatingStatus()).isEqualTo("TEMPORARILY_CLOSED");
        assertThat(spot.closedFrom()).isEqualTo(LocalDate.parse("2026-12-01"));
        assertThat(spot.closedUntil()).isEqualTo(LocalDate.parse("2027-02-28"));
        assertThat(spot.syncedAt()).isEqualTo(syncTime);
        assertThat(spot.detailUpdatedAt()).isEqualTo(syncTime);
        assertThat(spot.updatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
    }

    @Test
    @DisplayName("[F-06] 원천이 삭제로 알린 장소를 HIDDEN으로 바꾸고 숨긴 시각을 남긴 뒤 숨긴 수를 돌려준다")
    void markRemovedHidesActiveSpot() {
        // given
        publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(seoulCampsite("100", "솔숲 야영장")));
        clock.advance(NEXT_SYNC);

        // when
        int hidden = publicSpotSyncService.markRemoved(PublicSpotSource.GOCAMPING, List.of("100"));

        // then
        assertThat(hidden).isEqualTo(1);
        Instant removeTime = MutableClock.DEFAULT_INSTANT.plus(NEXT_SYNC);
        StoredSpot spot = findSpot(PublicSpotSource.GOCAMPING, "100");
        assertThat(spot.status()).isEqualTo("HIDDEN");
        assertThat(spot.updatedAt()).isEqualTo(removeTime);
        assertThat(spot.sourceRemovedAt()).isEqualTo(removeTime);
        assertThat(spot.detailUpdatedAt()).isEqualTo(removeTime);
        assertThat(spot.syncedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
    }

    @Test
    @DisplayName("[F-06] 같은 삭제 항목을 다시 처리하면 0을 돌려주고 이미 숨긴 장소의 시각을 바꾸지 않는다")
    void markRemovedTwiceIsIdempotent() {
        // given
        publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(seoulCampsite("100", "솔숲 야영장")));
        clock.advance(NEXT_SYNC);
        publicSpotSyncService.markRemoved(PublicSpotSource.GOCAMPING, List.of("100"));
        clock.advance(NEXT_SYNC);

        // when
        int hidden = publicSpotSyncService.markRemoved(PublicSpotSource.GOCAMPING, List.of("100"));

        // then
        assertThat(hidden).isZero();
        Instant firstRemoveTime = MutableClock.DEFAULT_INSTANT.plus(NEXT_SYNC);
        StoredSpot spot = findSpot(PublicSpotSource.GOCAMPING, "100");
        assertThat(spot.status()).isEqualTo("HIDDEN");
        assertThat(spot.updatedAt()).isEqualTo(firstRemoveTime);
        assertThat(spot.sourceRemovedAt()).isEqualTo(firstRemoveTime);
        assertThat(spot.detailUpdatedAt()).isEqualTo(firstRemoveTime);
    }

    @Test
    @DisplayName("[F-06] 관리자가 이미 숨긴 장소는 삭제 항목이 와도 건드리지도 표시하지도 않는다")
    void markRemovedLeavesAdminHiddenSpotUntouched() {
        // given
        publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(seoulCampsite("100", "솔숲 야영장")));
        setSpotStatus(findSpotId(PublicSpotSource.GOCAMPING, "100"), "HIDDEN");
        clock.advance(NEXT_SYNC);

        // when
        int hidden = publicSpotSyncService.markRemoved(PublicSpotSource.GOCAMPING, List.of("100"));

        // then
        assertThat(hidden).isZero();
        StoredSpot spot = findSpot(PublicSpotSource.GOCAMPING, "100");
        assertThat(spot.status()).isEqualTo("HIDDEN");
        assertThat(spot.sourceRemovedAt()).isNull();
        assertThat(spot.updatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
        assertThat(spot.detailUpdatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
    }

    @Test
    @DisplayName("[F-06] 관리자가 다시 공개한 장소(ACTIVE이고 삭제 표시가 남은 장소)는 삭제 항목이 또 와도 다시 숨기지 않는다")
    void markRemovedDoesNotHideAdminRestoredSpotAgain() {
        // given
        publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(seoulCampsite("100", "솔숲 야영장")));
        long spotId = findSpotId(PublicSpotSource.GOCAMPING, "100");
        Instant earlier = MutableClock.DEFAULT_INSTANT.minus(NEXT_SYNC);
        markSourceRemovedBySql(spotId, earlier);
        clock.advance(NEXT_SYNC);

        // when
        int hidden = publicSpotSyncService.markRemoved(PublicSpotSource.GOCAMPING, List.of("100"));

        // then
        assertThat(hidden).isZero();
        StoredSpot spot = findSpot(PublicSpotSource.GOCAMPING, "100");
        assertThat(spot.status()).isEqualTo("ACTIVE");
        assertThat(spot.sourceRemovedAt()).isEqualTo(earlier);
        assertThat(spot.updatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
    }

    @Test
    @DisplayName("[F-06] DB에 없는 외부 ID, 빈 외부 ID, 빈 목록은 숨긴 장소 없이 0을 돌려준다")
    void markRemovedIgnoresUnknownBlankAndEmptyIds() {
        // given
        publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(seoulCampsite("100", "솔숲 야영장")));
        String tooLong = "9".repeat(NormalizedPublicSpot.MAX_EXTERNAL_ID_LENGTH + 1);

        // when
        int unknown = publicSpotSyncService.markRemoved(PublicSpotSource.GOCAMPING, List.of("999", " ", "", tooLong));
        int empty = publicSpotSyncService.markRemoved(PublicSpotSource.GOCAMPING, List.of());

        // then
        assertThat(unknown).isZero();
        assertThat(empty).isZero();
        StoredSpot spot = findSpot(PublicSpotSource.GOCAMPING, "100");
        assertThat(spot.status()).isEqualTo("ACTIVE");
        assertThat(spot.sourceRemovedAt()).isNull();
    }

    @Test
    @DisplayName("[F-06] 지정한 출처의 장소만 숨기고, 외부 ID가 같은 다른 출처의 장소는 건드리지 않는다")
    void markRemovedHidesOnlyGivenSource() {
        // given
        publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(seoulCampsite("100", "솔숲 야영장")));
        publicSpotSyncService.upsert(PublicSpotSource.FOREST, List.of(seoulCampsite("100", "솔숲 휴양림")));
        publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(seoulCampsite("101", "갈대 야영장")));

        // when
        int hidden = publicSpotSyncService.markRemoved(PublicSpotSource.GOCAMPING, List.of("100", "999"));

        // then
        assertThat(hidden).isEqualTo(1);
        assertThat(findSpot(PublicSpotSource.GOCAMPING, "100").status()).isEqualTo("HIDDEN");
        StoredSpot forest = findSpot(PublicSpotSource.FOREST, "100");
        assertThat(forest.status()).isEqualTo("ACTIVE");
        assertThat(forest.sourceRemovedAt()).isNull();
        StoredSpot other = findSpot(PublicSpotSource.GOCAMPING, "101");
        assertThat(other.status()).isEqualTo("ACTIVE");
        assertThat(other.sourceRemovedAt()).isNull();
    }

    @Test
    @DisplayName("[F-06] 여러 장소를 한 번에 숨기면 숨긴 수만 세고, 이미 숨긴 장소는 세지 않는다")
    void markRemovedCountsOnlySpotsActuallyHidden() {
        // given
        publicSpotSyncService.upsert(
                PublicSpotSource.GOCAMPING,
                List.of(
                        seoulCampsite("100", "솔숲 야영장"),
                        seoulCampsite("101", "갈대 야영장"),
                        seoulCampsite("102", "달빛 야영장")));
        setSpotStatus(findSpotId(PublicSpotSource.GOCAMPING, "102"), "HIDDEN");

        // when
        int hidden = publicSpotSyncService.markRemoved(PublicSpotSource.GOCAMPING, Set.of("100", "101", "102"));

        // then
        assertThat(hidden).isEqualTo(2);
        assertThat(findSpot(PublicSpotSource.GOCAMPING, "102").sourceRemovedAt())
                .isNull();
    }

    @Test
    @DisplayName("[F-06] 원천 삭제로 숨긴 장소가 다시 오면 ACTIVE로 되돌리고 삭제 표시를 지우며 갱신 한 건으로 센다")
    void upsertRestoresSpotHiddenBySourceRemoval() {
        // given
        PublicSpotCommand command = seoulCampsite("100", "솔숲 야영장");
        publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(command));
        clock.advance(NEXT_SYNC);
        publicSpotSyncService.markRemoved(PublicSpotSource.GOCAMPING, List.of("100"));
        clock.advance(NEXT_SYNC);

        // when
        PublicSpotUpsertResult result = publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(command));

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(0, 1, 0, 0));
        Instant restoreTime = MutableClock.DEFAULT_INSTANT.plus(NEXT_SYNC.multipliedBy(2));
        StoredSpot spot = findSpot(PublicSpotSource.GOCAMPING, "100");
        assertThat(spot.status()).isEqualTo("ACTIVE");
        assertThat(spot.sourceRemovedAt()).isNull();
        assertThat(spot.updatedAt()).isEqualTo(restoreTime);
        assertThat(spot.detailUpdatedAt()).isEqualTo(restoreTime);
        assertThat(spot.syncedAt()).isEqualTo(restoreTime);
    }

    @Test
    @DisplayName("[F-06] 되돌리는 장소의 값도 바뀌었으면 그 장소를 갱신 한 건으로만 세고 새 값도 저장한다")
    void restoredSpotWithChangedFieldsIsCountedOnce() {
        // given
        publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(seoulCampsite("100", "솔숲 야영장")));
        publicSpotSyncService.markRemoved(PublicSpotSource.GOCAMPING, List.of("100"));
        clock.advance(NEXT_SYNC);
        PublicSpotCommand renamed = campsite(
                "100",
                "솔숲 야영장(새 이름)",
                SEOUL_LATITUDE,
                SEOUL_LONGITUDE,
                PublicSpotOperatingStatus.OPERATING,
                null,
                null);

        // when
        PublicSpotUpsertResult result = publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(renamed));

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(0, 1, 0, 0));
        StoredSpot spot = findSpot(PublicSpotSource.GOCAMPING, "100");
        assertThat(spot.status()).isEqualTo("ACTIVE");
        assertThat(spot.sourceRemovedAt()).isNull();
        assertThat(spot.name()).isEqualTo("솔숲 야영장(새 이름)");
        assertThat(spot.operatingStatus()).isEqualTo("OPERATING");
    }

    @Test
    @DisplayName("[F-06] 삭제 표시가 있지만 관리자가 이미 ACTIVE로 되돌린 장소는 삭제 표시만 지운다")
    void upsertOnlyClearsMarkerWhenSpotIsAlreadyActive() {
        // given
        PublicSpotCommand command = seoulCampsite("100", "솔숲 야영장");
        publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(command));
        markSourceRemovedBySql(findSpotId(PublicSpotSource.GOCAMPING, "100"), MutableClock.DEFAULT_INSTANT);
        clock.advance(NEXT_SYNC);

        // when
        PublicSpotUpsertResult result = publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(command));

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(0, 1, 0, 0));
        Instant syncTime = MutableClock.DEFAULT_INSTANT.plus(NEXT_SYNC);
        StoredSpot spot = findSpot(PublicSpotSource.GOCAMPING, "100");
        assertThat(spot.status()).isEqualTo("ACTIVE");
        assertThat(spot.sourceRemovedAt()).isNull();
        assertThat(spot.updatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
        assertThat(spot.detailUpdatedAt()).isEqualTo(syncTime);
    }

    @Test
    @DisplayName("[F-06] 관리자가 숨긴 장소(삭제 표시 없음)는 같은 데이터로 다시 적재해도 HIDDEN으로 남고 바뀌지 않은 것으로 센다")
    void upsertKeepsAdminHiddenSpotHidden() {
        // given
        PublicSpotCommand command = seoulCampsite("100", "솔숲 야영장");
        publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(command));
        setSpotStatus(findSpotId(PublicSpotSource.GOCAMPING, "100"), "HIDDEN");
        clock.advance(NEXT_SYNC);

        // when
        PublicSpotUpsertResult result = publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(command));

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(0, 0, 1, 0));
        StoredSpot spot = findSpot(PublicSpotSource.GOCAMPING, "100");
        assertThat(spot.status()).isEqualTo("HIDDEN");
        assertThat(spot.sourceRemovedAt()).isNull();
        assertThat(spot.updatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
        assertThat(spot.syncedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
    }

    private static PublicSpotCommand campsite(String externalId, String name, double latitude, double longitude) {
        return campsite(externalId, name, latitude, longitude, null, null, null);
    }

    private static PublicSpotCommand campsite(
            String externalId,
            String name,
            double latitude,
            double longitude,
            PublicSpotOperatingStatus operatingStatus,
            LocalDate closedFrom,
            LocalDate closedUntil) {
        Map<String, String> facilities = Map.of("sbrsCl", "전기,무선인터넷", "toiletCo", "4", "animalCmgCl", "불가능");
        return new PublicSpotCommand(
                externalId,
                name,
                latitude,
                longitude,
                "서울 중구 세종대로 110",
                "일반야영장",
                facilities,
                "02-000-0000",
                "https://example.com",
                operatingStatus,
                closedFrom,
                closedUntil);
    }

    private PublicSpotCommand seoulCampsite(String externalId, String name) {
        return campsite(externalId, name, SEOUL_LATITUDE, SEOUL_LONGITUDE);
    }

    private void markSourceRemovedBySql(long spotId, Instant removedAt) {
        jdbcTemplate.update(
                "UPDATE public_spot_detail SET source_removed_at = ? WHERE spot_id = ?",
                Timestamp.from(removedAt),
                spotId);
    }

    private void setSpotStatus(long spotId, String status) {
        jdbcTemplate.update("UPDATE spot SET status = ? WHERE id = ?", status, spotId);
    }

    private int count(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count;
    }

    private long findSpotId(PublicSpotSource source, String externalId) {
        Long spotId = jdbcTemplate.queryForObject(
                "SELECT spot_id FROM public_spot_detail WHERE source = ? AND external_id = ?",
                Long.class,
                source.name(),
                externalId);
        return spotId;
    }

    // 시각은 MyBatis가 Timestamp로 쓴 값이라, 같은 JDBC 변환을 거치도록 getTimestamp로 읽는다.
    private StoredSpot findSpot(PublicSpotSource source, String externalId) {
        return jdbcTemplate.queryForObject(
                "SELECT s.type, s.status, s.name, s.address, ST_Latitude(s.location) AS latitude,"
                        + " ST_Longitude(s.location) AS longitude, s.weather_nx, s.weather_ny, s.park_warning,"
                        + " s.created_at, s.updated_at, d.category, d.facilities, d.phone, d.homepage, d.synced_at,"
                        + " d.updated_at AS detail_updated_at, d.operating_status, d.closed_from, d.closed_until,"
                        + " d.source_removed_at"
                        + " FROM public_spot_detail d JOIN spot s ON s.id = d.spot_id"
                        + " WHERE d.source = ? AND d.external_id = ?",
                (rs, rowNum) -> new StoredSpot(
                        rs.getString("type"),
                        rs.getString("status"),
                        rs.getString("name"),
                        rs.getString("address"),
                        rs.getDouble("latitude"),
                        rs.getDouble("longitude"),
                        rs.getInt("weather_nx"),
                        rs.getInt("weather_ny"),
                        rs.getBoolean("park_warning"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant(),
                        rs.getString("category"),
                        rs.getString("facilities"),
                        rs.getString("phone"),
                        rs.getString("homepage"),
                        rs.getTimestamp("synced_at").toInstant(),
                        rs.getTimestamp("detail_updated_at").toInstant(),
                        rs.getString("operating_status"),
                        rs.getObject("closed_from", LocalDate.class),
                        rs.getObject("closed_until", LocalDate.class),
                        toInstantOrNull(rs.getTimestamp("source_removed_at"))),
                source.name(),
                externalId);
    }

    private record StoredSpot(
            String type,
            String status,
            String name,
            String address,
            double latitude,
            double longitude,
            int weatherNx,
            int weatherNy,
            boolean parkWarning,
            Instant createdAt,
            Instant updatedAt,
            String category,
            String facilities,
            String phone,
            String homepage,
            Instant syncedAt,
            Instant detailUpdatedAt,
            String operatingStatus,
            LocalDate closedFrom,
            LocalDate closedUntil,
            Instant sourceRemovedAt) {}

    private static Instant toInstantOrNull(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toInstant();
    }
}
