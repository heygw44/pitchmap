package com.pitchmap.publicdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.publicdata.application.SyncJobRunService.SyncJobStart;
import com.pitchmap.publicdata.domain.SyncJobType;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

// 픽스처의 공원 이름은 실제 공원이지만, 경계는 테스트에서 지어낸 작은 사각형이다. 좌표는 (경도 위도) 순서다.
// parks_20251231.tsv: 북한산(국립, 경도 126.9~127.0, 위도 37.6~37.7), 대둔산(도립, POLYGON으로 적은 경계), 강천산(군립, 조각 두 개)
// parks_20260630.tsv: 북한산 경계를 경도 127.1~127.2로 옮기고, 대둔산은 그대로 두고, 강천산을 빼고, 천마산(군립)을 더했다.
@IntegrationTest
class ParkBoundaryLoadServiceIntegrationTest {

    private static final String FIXTURE = "fixtures/publicdata/park-boundary/parks_20251231.tsv";
    private static final String NEWER_FIXTURE = "fixtures/publicdata/park-boundary/parks_20260630.tsv";
    private static final int PARK_COUNT = 3;
    private static final LocalDate FIXTURE_SOURCE_DATE = LocalDate.of(2025, 12, 31);
    private static final LocalDate NEWER_SOURCE_DATE = LocalDate.of(2026, 6, 30);

    @Autowired
    private ParkBoundaryLoadService parkBoundaryLoadService;

    @Autowired
    private SyncJobRunService syncJobRunService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("[F-06] 공원 경계 파일을 처음 적재하면 공원마다 경계 하나를 KDPA 출처, 구분, 파일 기준일과 함께 저장한다")
    void firstLoadStoresOneBoundaryPerParkWithSourceAndSourceDate() throws IOException {
        // when
        ParkBoundaryLoadResult result =
                parkBoundaryLoadService.load(fixture(FIXTURE)).orElseThrow();

        // then
        assertThat(result).isEqualTo(new ParkBoundaryLoadResult(FIXTURE_SOURCE_DATE, PARK_COUNT, PARK_COUNT, 0));
        assertThat(areaRows())
                .containsExactlyInAnyOrder(
                        area("북한산", "NATIONAL_PARK", FIXTURE_SOURCE_DATE),
                        area("대둔산", "PROVINCIAL_PARK", FIXTURE_SOURCE_DATE),
                        area("강천산", "COUNTY_PARK", FIXTURE_SOURCE_DATE));
        assertThat(runs()).singleElement().satisfies(run -> {
            assertThat(run.status()).isEqualTo("COMPLETED");
            assertThat(run.progressCursor()).isNull();
            assertThat(run.processedCount()).isEqualTo(PARK_COUNT);
            assertThat(run.skippedCount()).isZero();
            assertThat(run.finished()).isTrue();
        });
    }

    @Test
    @DisplayName("[F-06] 파일의 (경도 위도) 좌표를 경계의 경도와 위도로 저장해서, 경계 안의 점만 포함한다고 판정한다")
    void storesBoundaryInLongitudeLatitudeOrder() throws IOException {
        // when
        parkBoundaryLoadService.load(fixture(FIXTURE));

        // then: 북한산 경계 첫 점은 파일에 (126.9 37.6)으로 적혀 있다.
        Map<String, Object> firstPoint = jdbcTemplate.queryForMap(
                "SELECT ST_Longitude(p.point) AS longitude, ST_Latitude(p.point) AS latitude FROM"
                        + " (SELECT ST_PointN(ST_ExteriorRing(ST_GeometryN(boundary, 1)), 1) AS point"
                        + " FROM protected_area WHERE name = '북한산') AS p");
        assertThat(((Number) firstPoint.get("longitude")).doubleValue()).isCloseTo(126.9, within(1e-9));
        assertThat(((Number) firstPoint.get("latitude")).doubleValue()).isCloseTo(37.6, within(1e-9));
        assertThat(contains("북한산", 126.95, 37.65)).isTrue();
        assertThat(contains("북한산", 127.05, 37.65)).isFalse();
        assertThat(contains("강천산", 127.12, 35.42)).isTrue();
        assertThat(contains("강천산", 127.07, 35.42)).isFalse();
    }

    @Test
    @DisplayName("[F-06] POLYGON으로 적힌 경계도 MULTIPOLYGON으로 저장한다")
    void storesPolygonRowAsMultiPolygon() throws IOException {
        // when
        parkBoundaryLoadService.load(fixture(FIXTURE));

        // then
        assertThat(jdbcTemplate.queryForList(
                        "SELECT DISTINCT ST_GeometryType(boundary) FROM protected_area", String.class))
                .singleElement()
                .satisfies(type -> assertThat(type).isEqualToIgnoringCase("MULTIPOLYGON"));
        assertThat(contains("대둔산", 127.32, 36.12)).isTrue();
    }

    @Test
    @DisplayName("[F-06] 같은 파일을 다시 적재하면 공원 수는 그대로이고, 같은 행의 경계를 다시 써서 갱신한 것으로 센다")
    void reloadingSameFileKeepsOneRowPerPark() throws IOException {
        // given
        parkBoundaryLoadService.load(fixture(FIXTURE));
        List<Map<String, Object>> before = identityRows();
        clock.advance(Duration.ofDays(1));

        // when
        ParkBoundaryLoadResult result =
                parkBoundaryLoadService.load(fixture(FIXTURE)).orElseThrow();

        // then
        assertThat(result).isEqualTo(new ParkBoundaryLoadResult(FIXTURE_SOURCE_DATE, PARK_COUNT, 0, PARK_COUNT));
        assertThat(identityRows()).isEqualTo(before);
        assertThat(countRows("protected_area WHERE updated_at > created_at")).isEqualTo(PARK_COUNT);
        assertThat(runs()).hasSize(2).allSatisfy(run -> assertThat(run.status()).isEqualTo("COMPLETED"));
    }

    @Test
    @DisplayName("[F-06] 기준일이 다른 파일을 적재하면 있는 공원의 경계와 기준일을 바꾸고, 새 공원을 더하고, 파일에서 빠진 공원은 남긴다")
    void loadingNewerFileUpdatesExistingInsertsNewAndKeepsMissing() throws IOException {
        // given
        parkBoundaryLoadService.load(fixture(FIXTURE));
        long bukhansanId = idOf("북한산");
        clock.advance(Duration.ofDays(1));

        // when
        ParkBoundaryLoadResult result =
                parkBoundaryLoadService.load(fixture(NEWER_FIXTURE)).orElseThrow();

        // then
        assertThat(result).isEqualTo(new ParkBoundaryLoadResult(NEWER_SOURCE_DATE, PARK_COUNT, 1, 2));
        assertThat(areaRows())
                .containsExactlyInAnyOrder(
                        area("북한산", "NATIONAL_PARK", NEWER_SOURCE_DATE),
                        area("대둔산", "PROVINCIAL_PARK", NEWER_SOURCE_DATE),
                        area("강천산", "COUNTY_PARK", FIXTURE_SOURCE_DATE),
                        area("천마산", "COUNTY_PARK", NEWER_SOURCE_DATE));
        assertThat(idOf("북한산")).isEqualTo(bukhansanId);
        assertThat(contains("북한산", 127.15, 37.65)).isTrue();
        assertThat(contains("북한산", 126.95, 37.65)).isFalse();
    }

    @Test
    @DisplayName("[F-06] 파일이 없으면 예외를 다시 던지고 실행 기록을 실패로 남긴다")
    void missingFileFailsRunAndRethrows() {
        // given
        Path missing = tempDir.resolve("parks_20251231.tsv");

        // when, then
        assertThatThrownBy(() -> parkBoundaryLoadService.load(missing)).isInstanceOf(UncheckedIOException.class);
        assertThat(countRows("protected_area")).isZero();
        assertThat(runs()).singleElement().satisfies(run -> {
            assertThat(run.status()).isEqualTo("FAILED");
            assertThat(run.errorMessage()).startsWith("UncheckedIOException: ");
            assertThat(run.finished()).isTrue();
        });
    }

    @Test
    @DisplayName("[F-06] 형식이 깨진 줄이 있으면 공원을 하나도 저장하지 않고, 줄 번호를 실패 기록에 남긴다")
    void invalidFileFailsRunWithoutStoringAnyPark() throws IOException {
        // given: 세 번째 줄의 구분이 적재 대상이 아니다.
        String content =
                Files.readString(fixture(FIXTURE), StandardCharsets.UTF_8).replace("PROVINCIAL_PARK", "URBAN_PARK");
        Path file = Files.writeString(tempDir.resolve("parks_20251231.tsv"), content, StandardCharsets.UTF_8);

        // when, then
        assertThatThrownBy(() -> parkBoundaryLoadService.load(file)).isInstanceOf(IllegalStateException.class);
        assertThat(countRows("protected_area")).isZero();
        assertThat(runs()).singleElement().satisfies(run -> {
            assertThat(run.status()).isEqualTo("FAILED");
            assertThat(run.errorMessage()).startsWith("IllegalStateException: ").contains("line=3");
        });
    }

    @Test
    @DisplayName("[F-06] 파일 경로가 설정되지 않았으면 예외를 다시 던지고 설정 키를 실패 기록에 남긴다")
    void missingFilePathFailsRunWithPropertyName() {
        // when, then
        assertThatThrownBy(() -> parkBoundaryLoadService.load(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ParkBoundaryLoadProperties.FILE_PROPERTY);
        assertThat(runs()).singleElement().satisfies(run -> {
            assertThat(run.status()).isEqualTo("FAILED");
            assertThat(run.errorMessage()).contains(ParkBoundaryLoadProperties.FILE_PROPERTY);
        });
    }

    @Test
    @DisplayName("[F-06] 인자 없이 호출하면 설정한 경로의 파일을 적재한다")
    void loadUsesConfiguredFile() {
        // when
        ParkBoundaryLoadResult result = parkBoundaryLoadService.load().orElseThrow();

        // then
        assertThat(result).isEqualTo(new ParkBoundaryLoadResult(FIXTURE_SOURCE_DATE, PARK_COUNT, PARK_COUNT, 0));
        assertThat(countRows("protected_area WHERE source = 'KDPA'")).isEqualTo(PARK_COUNT);
    }

    @Test
    @DisplayName("[F-06] 다른 공원 경계 적재가 실행 중이면 시작하지 않고 빈 값을 돌려준다")
    void loadReturnsEmptyWhileAnotherRunIsInProgress() {
        // given
        syncJobRunService
                .begin(SyncJobType.PARK_BOUNDARY, Duration.ofMinutes(30))
                .orElseThrow();

        // when, then
        assertThat(parkBoundaryLoadService.load()).isEmpty();
        assertThat(countRows("protected_area")).isZero();
        assertThat(runs())
                .singleElement()
                .satisfies(run -> assertThat(run.status()).isEqualTo("RUNNING"));
    }

    @Test
    @DisplayName("[F-06] 호출하는 쪽이 먼저 시작한 실행 기록으로 run을 호출하면 설정한 파일을 적재하고 그 기록을 완료로 바꾼다")
    void runLoadsConfiguredFileWithStartedRun() {
        // given
        SyncJobStart start = syncJobRunService
                .begin(SyncJobType.PARK_BOUNDARY, Duration.ofMinutes(30))
                .orElseThrow();

        // when
        ParkBoundaryLoadResult result = parkBoundaryLoadService.run(start);

        // then
        assertThat(result).isEqualTo(new ParkBoundaryLoadResult(FIXTURE_SOURCE_DATE, PARK_COUNT, PARK_COUNT, 0));
        assertThat(countRows("protected_area")).isEqualTo(PARK_COUNT);
        assertThat(runs()).singleElement().satisfies(run -> {
            assertThat(run.id()).isEqualTo(start.runId());
            assertThat(run.status()).isEqualTo("COMPLETED");
            assertThat(run.processedCount()).isEqualTo(PARK_COUNT);
        });
    }

    private static Path fixture(String path) throws IOException {
        return new ClassPathResource(path).getFile().toPath();
    }

    private int countRows(String tableAndCondition) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableAndCondition, Integer.class);
    }

    private long idOf(String name) {
        return jdbcTemplate.queryForObject("SELECT id FROM protected_area WHERE name = ?", Long.class, name);
    }

    private boolean contains(String name, double longitude, double latitude) {
        Integer contained = jdbcTemplate.queryForObject(
                "SELECT ST_Contains(boundary, ST_GeomFromText(?, 4326, 'axis-order=long-lat'))"
                        + " FROM protected_area WHERE name = ?",
                Integer.class,
                "POINT(" + longitude + " " + latitude + ")",
                name);
        return contained != null && contained == 1;
    }

    private static AreaRow area(String name, String areaType, LocalDate sourceDate) {
        return new AreaRow(name, areaType, "KDPA", sourceDate.toString());
    }

    private List<AreaRow> areaRows() {
        return jdbcTemplate.query(
                "SELECT name, area_type, source, CAST(source_date AS CHAR) AS source_date FROM protected_area",
                (rs, rowNum) -> new AreaRow(
                        rs.getString("name"),
                        rs.getString("area_type"),
                        rs.getString("source"),
                        rs.getString("source_date")));
    }

    private List<Map<String, Object>> identityRows() {
        return jdbcTemplate.queryForList(
                "SELECT id, name, area_type, source, created_at FROM protected_area ORDER BY id");
    }

    private List<RunRow> runs() {
        return jdbcTemplate.query(
                "SELECT id, status, progress_cursor, processed_count, skipped_count, error_message, finished_at"
                        + " FROM sync_job_run WHERE job_type = 'PARK_BOUNDARY' ORDER BY id",
                (rs, rowNum) -> new RunRow(
                        rs.getLong("id"),
                        rs.getString("status"),
                        rs.getString("progress_cursor"),
                        rs.getInt("processed_count"),
                        rs.getInt("skipped_count"),
                        rs.getString("error_message"),
                        rs.getObject("finished_at") != null));
    }

    private record AreaRow(String name, String areaType, String source, String sourceDate) {}

    private record RunRow(
            long id,
            String status,
            String progressCursor,
            int processedCount,
            int skippedCount,
            String errorMessage,
            boolean finished) {}
}
