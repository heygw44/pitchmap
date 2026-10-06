package com.pitchmap.spot.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.spot.domain.GeoPoint;
import com.pitchmap.spot.domain.ParkAreaJudgement;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ParkAreaJudgeServiceIntegrationTest {

    // 경계 WKT는 경계 파일과 같이 (경도 위도) 순서로 쓰고, 넣을 때 axis-order=long-lat 옵션을 붙인다.
    // 경도 127.0~127.5, 위도 37.0~37.5인 사각형이다.
    private static final String PARK_SQUARE = "MULTIPOLYGON(((127 37, 127.5 37, 127.5 37.5, 127 37.5, 127 37)))";
    private static final GeoPoint INSIDE_PARK = new GeoPoint(37.25, 127.25);
    private static final GeoPoint OUTSIDE_EVERY_AREA = new GeoPoint(37.25, 127.75);

    // 장소를 저장한 시각이다. 재판정이 updated_at을 바꾸지 않는지 보려고 판정 시각과 다르게 둔다.
    private static final Instant SAVED_AT = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant JUDGED_AT = Instant.parse("2026-10-06T01:00:00Z");

    @Autowired
    private ParkAreaJudgeService parkAreaJudgeService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName("[F-07] 좌표가 공원 경계 안이면 경고 대상으로 판정하고 그 경계의 ID를 돌려준다")
    void judgesPointInsideBoundaryAsWarned() {
        // given
        long areaId = insertProtectedArea(PARK_SQUARE);
        clock.setInstant(JUDGED_AT);

        // when
        ParkAreaJudgement judgement = parkAreaJudgeService.judge(INSIDE_PARK);

        // then
        assertThat(judgement.parkWarning()).isTrue();
        assertThat(judgement.protectedAreaId()).isEqualTo(areaId);
        assertThat(judgement.checkedAt()).isEqualTo(JUDGED_AT);
    }

    @Test
    @DisplayName("[F-07] 좌표를 포함하는 공원 경계가 없으면 경고 없이 판정하고 경계 ID는 비운다")
    void judgesPointOutsideEveryBoundaryAsNotWarned() {
        // given
        insertProtectedArea(PARK_SQUARE);
        clock.setInstant(JUDGED_AT);

        // when
        ParkAreaJudgement judgement = parkAreaJudgeService.judge(OUTSIDE_EVERY_AREA);

        // then
        assertThat(judgement.parkWarning()).isFalse();
        assertThat(judgement.protectedAreaId()).isNull();
        assertThat(judgement.checkedAt()).isEqualTo(JUDGED_AT);
    }

    @Test
    @DisplayName("[F-07] 여러 조각으로 된 경계에서 두 번째 조각 안의 좌표도 경고 대상으로 판정한다")
    void judgesPointInsideSecondPolygonOfMultiPolygonAsWarned() {
        // given: 첫 조각은 경도 126.0~126.2, 위도 35.0~35.2이고 두 번째 조각은 경도 128.0~128.2, 위도 36.0~36.2다.
        long areaId = insertProtectedArea("MULTIPOLYGON(((126 35, 126.2 35, 126.2 35.2, 126 35.2, 126 35)),"
                + " ((128 36, 128.2 36, 128.2 36.2, 128 36.2, 128 36)))");

        // when
        ParkAreaJudgement judgement = parkAreaJudgeService.judge(new GeoPoint(36.1, 128.1));

        // then
        assertThat(judgement.parkWarning()).isTrue();
        assertThat(judgement.protectedAreaId()).isEqualTo(areaId);
    }

    @Test
    @DisplayName("[F-07] 위도와 경도를 뒤바꾼 좌표는 경계 밖으로 판정한다")
    void judgesSwappedCoordinateAsOutside() {
        // given: 경도 10~11, 위도 20~21인 사각형이다. 판정 SQL이 위도와 경도를 뒤바꿔 점을 만들면 두 좌표의 판정이 서로 바뀐다.
        long areaId = insertProtectedArea("MULTIPOLYGON(((10 20, 11 20, 11 21, 10 21, 10 20)))");

        // when
        ParkAreaJudgement inside = parkAreaJudgeService.judge(new GeoPoint(20.5, 10.5));
        ParkAreaJudgement swapped = parkAreaJudgeService.judge(new GeoPoint(10.5, 20.5));

        // then
        assertThat(inside.protectedAreaId()).isEqualTo(areaId);
        assertThat(swapped.parkWarning()).isFalse();
        assertThat(swapped.protectedAreaId()).isNull();
    }

    @Test
    @DisplayName("[F-07] 겹친 경계 두 곳에 들어가는 좌표는 ID가 작은 경계로 판정한다")
    void choosesSmallestIdWhenBoundariesOverlap() {
        // given: 먼저 넣은 작은 사각형을 나중에 넣은 큰 사각형이 덮는다.
        long smallerId = insertProtectedArea(PARK_SQUARE);
        long largerId = insertProtectedArea("MULTIPOLYGON(((126.5 36.5, 128 36.5, 128 38, 126.5 38, 126.5 36.5)))");

        // when
        ParkAreaJudgement judgement = parkAreaJudgeService.judge(INSIDE_PARK);

        // then
        assertThat(smallerId).isLessThan(largerId);
        assertThat(judgement.protectedAreaId()).isEqualTo(smallerId);
    }

    @Test
    @DisplayName("[F-07] 저장 크기가 큰 경계가 여럿 있어도 좌표를 판정한다")
    void judgesWhenManyBoundariesAreLarge() {
        // given: 꼭짓점 3만 개(WKB 약 480KB)인 원형 경계 10개가 0.5도 간격으로 떨어져 있다. 이 조건에서 경계를 ID로 정렬해 고르는 쿼리
        // (ORDER BY id LIMIT 1)는 "Out of sort memory" 오류를 냈다.
        long areaId = 0;
        for (int i = 0; i < 10; i++) {
            long id = insertProtectedArea(circleWkt(126.0 + i * 0.5, 36.0, 0.05, 30_000));
            if (i == 6) {
                areaId = id;
            }
        }
        GeoPoint center = new GeoPoint(36.0, 129.0);
        GeoPoint outside = new GeoPoint(36.0, 128.75);
        long bakjiId = insertSpot("BAKJI", "ACTIVE", center);
        clock.setInstant(JUDGED_AT);

        // when
        ParkAreaJudgement inside = parkAreaJudgeService.judge(center);
        ParkAreaJudgement away = parkAreaJudgeService.judge(outside);
        parkAreaJudgeService.rejudgeBakjis();

        // then
        assertThat(inside.parkWarning()).isTrue();
        assertThat(inside.protectedAreaId()).isEqualTo(areaId);
        assertThat(away.parkWarning()).isFalse();
        assertThat(away.protectedAreaId()).isNull();
        assertThat(judgementOf(bakjiId)).isEqualTo(new SpotJudgementRow(true, areaId, JUDGED_AT, SAVED_AT));
    }

    @Test
    @DisplayName("[F-06][F-07] 재판정은 상태와 상관없이 모든 박지를 새 경계로 다시 판정하고, 박지가 아닌 장소와 updated_at은 건드리지 않는다")
    void rejudgesEveryBakjiWithCurrentBoundaries() {
        // given: 예전 경계는 경도 129.0~129.5, 위도 35.0~35.5라서 지금은 어느 박지도 포함하지 않는다.
        long areaId = insertProtectedArea(PARK_SQUARE);
        long staleAreaId = insertProtectedArea("MULTIPOLYGON(((129 35, 129.5 35, 129.5 35.5, 129 35.5, 129 35)))");
        long insideId = insertSpot("BAKJI", "ACTIVE", INSIDE_PARK);
        long outsideId = insertSpot("BAKJI", "ACTIVE", OUTSIDE_EVERY_AREA);
        long staleWarnedId = insertSpot("BAKJI", "ACTIVE", OUTSIDE_EVERY_AREA);
        jdbcTemplate.update(
                "UPDATE spot SET park_warning = TRUE, protected_area_id = ?, area_checked_at = ? WHERE id = ?",
                staleAreaId,
                Timestamp.from(SAVED_AT),
                staleWarnedId);
        long hiddenId = insertSpot("BAKJI", "HIDDEN", INSIDE_PARK);
        long deletedId = insertSpot("BAKJI", "DELETED", INSIDE_PARK);
        long pendingId = insertSpot("BAKJI", "PENDING_REVIEW", INSIDE_PARK);
        long campsiteId = insertSpot("CAMPSITE", "ACTIVE", INSIDE_PARK);
        clock.setInstant(JUDGED_AT);

        // when
        int judged = parkAreaJudgeService.rejudgeBakjis();

        // then
        assertThat(judged).isEqualTo(6);
        assertThat(judgementOf(insideId)).isEqualTo(new SpotJudgementRow(true, areaId, JUDGED_AT, SAVED_AT));
        assertThat(judgementOf(outsideId)).isEqualTo(new SpotJudgementRow(false, null, JUDGED_AT, SAVED_AT));
        assertThat(judgementOf(staleWarnedId)).isEqualTo(new SpotJudgementRow(false, null, JUDGED_AT, SAVED_AT));
        assertThat(judgementOf(hiddenId)).isEqualTo(new SpotJudgementRow(true, areaId, JUDGED_AT, SAVED_AT));
        assertThat(judgementOf(deletedId)).isEqualTo(new SpotJudgementRow(true, areaId, JUDGED_AT, SAVED_AT));
        assertThat(judgementOf(pendingId)).isEqualTo(new SpotJudgementRow(true, areaId, JUDGED_AT, SAVED_AT));
        assertThat(judgementOf(campsiteId)).isEqualTo(new SpotJudgementRow(false, null, null, SAVED_AT));
    }

    @Test
    @DisplayName("[F-06][F-07] 재판정은 한 번에 기록하는 묶음(1,000건)보다 박지가 많아도 모든 박지를 판정한다")
    void rejudgesEveryBakjiAcrossUpdateChunks() {
        // given: 경계 안 박지 600건과 경계 밖 박지 401건을 한 번의 배치로 넣는다.
        long areaId = insertProtectedArea(PARK_SQUARE);
        String prefix = TestSequence.unique("대량박지");
        int insideCount = 600;
        int outsideCount = 401;
        insertBakjiBatch(prefix + "-in", INSIDE_PARK, insideCount);
        insertBakjiBatch(prefix + "-out", OUTSIDE_EVERY_AREA, outsideCount);
        clock.setInstant(JUDGED_AT);

        // when
        int judged = parkAreaJudgeService.rejudgeBakjis();

        // then
        assertThat(judged).isGreaterThanOrEqualTo(insideCount + outsideCount);
        assertThat(countBatchSpots(prefix + "-in%", true, areaId)).isEqualTo(insideCount);
        assertThat(countBatchSpots(prefix + "-out%", false, null)).isEqualTo(outsideCount);
    }

    private void insertBakjiBatch(String namePrefix, GeoPoint at, int count) {
        String wkt = String.format(Locale.ROOT, "POINT(%s %s)", at.latitude(), at.longitude());
        jdbcTemplate.batchUpdate(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                        + " VALUES ('BAKJI', ?, ST_GeomFromText(?, 4326), 60, 127, 'ACTIVE', ?, ?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ps.setString(1, namePrefix + "-" + i);
                        ps.setString(2, wkt);
                        ps.setTimestamp(3, Timestamp.from(SAVED_AT));
                        ps.setTimestamp(4, Timestamp.from(SAVED_AT));
                    }

                    @Override
                    public int getBatchSize() {
                        return count;
                    }
                });
    }

    // 이름이 namePattern인 박지 중 판정 결과가 기대와 같고 판정 시각이 기록된 행의 수를 센다.
    private int countBatchSpots(String namePattern, boolean expectedWarning, Long expectedAreaId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM spot WHERE name LIKE ? AND park_warning = ?"
                        + " AND protected_area_id <=> ? AND area_checked_at = ?",
                Integer.class,
                namePattern,
                expectedWarning,
                expectedAreaId,
                Timestamp.from(JUDGED_AT));
    }

    // 중심에서 반지름만큼 떨어진 꼭짓점 vertexCount개를 (경도 위도) 순서로 이어, 첫 꼭짓점으로 닫은 원 모양 다각형을 만든다.
    private String circleWkt(double centerLng, double centerLat, double radius, int vertexCount) {
        StringBuilder wkt = new StringBuilder("MULTIPOLYGON(((");
        for (int i = 0; i < vertexCount; i++) {
            double angle = 2 * Math.PI * i / vertexCount;
            wkt.append(String.format(
                    Locale.ROOT,
                    "%.7f %.7f, ",
                    centerLng + radius * Math.cos(angle),
                    centerLat + radius * Math.sin(angle)));
        }
        wkt.append(String.format(Locale.ROOT, "%.7f %.7f)))", centerLng + radius, centerLat));
        return wkt.toString();
    }

    private long insertProtectedArea(String longLatWkt) {
        String name = TestSequence.unique("경계");
        jdbcTemplate.update(
                "INSERT INTO protected_area (name, area_type, source, source_date, boundary, created_at, updated_at)"
                        + " VALUES (?, 'NATIONAL_PARK', 'KDPA', '2026-01-01',"
                        + " ST_GeomFromText(?, 4326, 'axis-order=long-lat'), NOW(6), NOW(6))",
                name,
                longLatWkt);
        return jdbcTemplate.queryForObject("SELECT id FROM protected_area WHERE name = ?", Long.class, name);
    }

    // 장소 좌표는 SRID 4326 WKT 기본 순서인 (위도 경도)로 넣는다. 그래서 판정 SQL과 다른 경로로 좌표를 만든다.
    private long insertSpot(String type, String status, GeoPoint at) {
        String name = TestSequence.unique(type);
        jdbcTemplate.update(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                        + " VALUES (?, ?, ST_GeomFromText(?, 4326), 60, 127, ?, ?, ?)",
                type,
                name,
                String.format(Locale.ROOT, "POINT(%s %s)", at.latitude(), at.longitude()),
                status,
                Timestamp.from(SAVED_AT),
                Timestamp.from(SAVED_AT));
        return jdbcTemplate.queryForObject("SELECT id FROM spot WHERE name = ?", Long.class, name);
    }

    private SpotJudgementRow judgementOf(long spotId) {
        return jdbcTemplate.queryForObject(
                "SELECT park_warning, protected_area_id, area_checked_at, updated_at FROM spot WHERE id = ?",
                (rs, rowNum) -> {
                    Timestamp checkedAt = rs.getTimestamp("area_checked_at");
                    return new SpotJudgementRow(
                            rs.getBoolean("park_warning"),
                            rs.getObject("protected_area_id", Long.class),
                            checkedAt == null ? null : checkedAt.toInstant(),
                            rs.getTimestamp("updated_at").toInstant());
                },
                spotId);
    }

    private record SpotJudgementRow(
            boolean parkWarning, Long protectedAreaId, Instant areaCheckedAt, Instant updatedAt) {}
}
