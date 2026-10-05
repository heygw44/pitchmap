package com.pitchmap.publicdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
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

// 픽스처(fixtures/publicdata/forest/forest_20260325.csv)는 실제 파일
// (산림청 국립자연휴양림관리소_국립자연휴양림 예약 정책_20260325.csv)에서 휴양림 3곳의 행을 옮긴 것이다.
// 0101 유명산 자연휴양림 3행, 0116 화천숲속 야영장 2행, 0305 금산 자연휴양림 2행이라 행은 7개이고 휴양림은 3곳이다.
// 위도가 abc인 기관아이디 9999 행은 좌표를 읽지 못하는 경우를 확인하려고 테스트에서 지어낸 행이다.
@IntegrationTest
class ForestLoadServiceIntegrationTest {

    private static final String FIXTURE = "fixtures/publicdata/forest/forest_20260325.csv";
    private static final int FOREST_COUNT = 3;
    private static final LocalDate FIXTURE_SOURCE_DATE = LocalDate.of(2026, 3, 25);
    private static final String INVALID_COORDINATE_ROW =
            "9999,지어낸 자연휴양림,선착순 예약정책,선착순 예약정책,2026-03-25~2026-05-05,2026-03-25~2026-05-05, 강원,abc,"
                    + "127.5,033-000-0000,http://www.foresttrip.go.kr/9999";

    @Autowired
    private ForestLoadService forestLoadService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("[F-06] 휴양림 파일을 처음 적재하면 기관아이디마다 장소 하나를 FOREST로 저장하고 출처와 기준일을 남긴다")
    void firstLoadStoresOneForestSpotPerInstitutionWithSourceDate() throws IOException {
        // when
        ForestLoadResult result = forestLoadService.load(fixture()).orElseThrow();

        // then
        assertThat(result).isEqualTo(new ForestLoadResult(FIXTURE_SOURCE_DATE, FOREST_COUNT, FOREST_COUNT, 0, 0, 0));
        assertThat(jdbcTemplate.queryForList("SELECT DISTINCT type FROM spot", String.class))
                .containsExactly("FOREST");
        assertThat(jdbcTemplate.queryForList("SELECT DISTINCT status FROM spot", String.class))
                .containsExactly("ACTIVE");
        assertThat(detailRows())
                .containsExactly(
                        detail("0101", "유명산 자연휴양림", "031-589-5487", "http://www.foresttrip.go.kr/0101"),
                        detail("0116", "화천숲속 야영장", "033-441-4466", "http://www.foresttrip.go.kr/0116"),
                        detail(
                                "0305",
                                "금산 자연휴양림",
                                "041-754-0102",
                                "https://www.foresttrip.go.kr/indvz/main.do?hmpgId=0305"));
        assertThat(runs()).singleElement().satisfies(run -> {
            assertThat(run.status()).isEqualTo("COMPLETED");
            assertThat(run.progressCursor()).isNull();
            assertThat(run.processedCount()).isEqualTo(FOREST_COUNT);
            assertThat(run.skippedCount()).isZero();
            assertThat(run.finished()).isTrue();
        });
    }

    @Test
    @DisplayName("[F-06] 파일의 위도와 경도를 그대로 장소 좌표의 위도와 경도로 저장한다")
    void storesFileLatitudeAndLongitudeAsSpotLocation() throws IOException {
        // when
        forestLoadService.load(fixture());

        // then
        Map<String, Object> location = jdbcTemplate.queryForMap(
                "SELECT ST_Latitude(s.location) AS latitude, ST_Longitude(s.location) AS longitude FROM spot s"
                        + " JOIN public_spot_detail d ON d.spot_id = s.id WHERE d.external_id = '0101'");
        assertThat(((Number) location.get("latitude")).doubleValue()).isCloseTo(37.593195, within(1e-9));
        assertThat(((Number) location.get("longitude")).doubleValue()).isCloseTo(127.49145051, within(1e-9));
    }

    @Test
    @DisplayName("[F-06] 같은 파일을 다시 적재하면 장소 수와 값이 그대로이고 모두 바뀌지 않은 것으로 센다")
    void reloadingSameFileLeavesSpotsUnchanged() throws IOException {
        // given
        forestLoadService.load(fixture());
        List<Map<String, Object>> spotsBefore = spotRows();
        List<Map<String, Object>> detailsBefore = detailRowsWithTimestamps();
        clock.advance(Duration.ofDays(1));

        // when
        ForestLoadResult result = forestLoadService.load(fixture()).orElseThrow();

        // then
        assertThat(result).isEqualTo(new ForestLoadResult(FIXTURE_SOURCE_DATE, FOREST_COUNT, 0, 0, FOREST_COUNT, 0));
        assertThat(spotRows()).isEqualTo(spotsBefore);
        assertThat(detailRowsWithTimestamps()).isEqualTo(detailsBefore);
        assertThat(runs()).hasSize(2).allSatisfy(run -> assertThat(run.status()).isEqualTo("COMPLETED"));
    }

    @Test
    @DisplayName("[F-06] 기준일이 다른 파일을 적재하면 상세의 기준일을 바꾸고 갱신한 것으로 센다")
    void loadingFileWithNewSourceDateUpdatesSourceDate() throws IOException {
        // given
        forestLoadService.load(fixture());
        Path newerFile = Files.copy(fixture(), tempDir.resolve("forest_20260901.csv"));
        clock.advance(Duration.ofDays(1));

        // when
        ForestLoadResult result = forestLoadService.load(newerFile).orElseThrow();

        // then
        LocalDate newSourceDate = LocalDate.of(2026, 9, 1);
        assertThat(result).isEqualTo(new ForestLoadResult(newSourceDate, FOREST_COUNT, 0, FOREST_COUNT, 0, 0));
        assertThat(jdbcTemplate.queryForList(
                        "SELECT DISTINCT CAST(source_date AS CHAR) FROM public_spot_detail", String.class))
                .containsExactly(newSourceDate.toString());
        assertThat(countRows("spot")).isEqualTo(FOREST_COUNT);
    }

    @Test
    @DisplayName("[F-06] 좌표를 숫자로 읽지 못한 휴양림만 건너뛰고, 건너뛴 수를 결과와 실행 기록에 남긴다")
    void skipsForestWithInvalidCoordinate() throws IOException {
        // given
        String content = Files.readString(fixture(), StandardCharsets.UTF_8) + INVALID_COORDINATE_ROW + "\n";
        Path file = Files.writeString(tempDir.resolve("forest_20260325.csv"), content, StandardCharsets.UTF_8);

        // when
        ForestLoadResult result = forestLoadService.load(file).orElseThrow();

        // then
        assertThat(result)
                .isEqualTo(new ForestLoadResult(FIXTURE_SOURCE_DATE, FOREST_COUNT + 1, FOREST_COUNT, 0, 0, 1));
        assertThat(countRows("public_spot_detail WHERE external_id = '9999'")).isZero();
        assertThat(runs()).singleElement().satisfies(run -> {
            assertThat(run.status()).isEqualTo("COMPLETED");
            assertThat(run.processedCount()).isEqualTo(FOREST_COUNT + 1);
            assertThat(run.skippedCount()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("[F-06] 파일이 없으면 예외를 다시 던지고 실행 기록을 실패로 남긴다")
    void missingFileFailsRunAndRethrows() {
        // given
        Path missing = tempDir.resolve("forest_20260325.csv");

        // when, then
        assertThatThrownBy(() -> forestLoadService.load(missing)).isInstanceOf(UncheckedIOException.class);
        assertThat(countRows("spot")).isZero();
        assertThat(runs()).singleElement().satisfies(run -> {
            assertThat(run.status()).isEqualTo("FAILED");
            assertThat(run.errorMessage()).startsWith("UncheckedIOException: ");
            assertThat(run.finished()).isTrue();
        });
    }

    @Test
    @DisplayName("[F-06] 파일 경로가 설정되지 않았으면 예외를 다시 던지고 설정 키를 실패 기록에 남긴다")
    void missingFilePathFailsRunWithPropertyName() {
        // when, then
        assertThatThrownBy(() -> forestLoadService.load(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ForestLoadProperties.FILE_PROPERTY);
        assertThat(runs()).singleElement().satisfies(run -> {
            assertThat(run.status()).isEqualTo("FAILED");
            assertThat(run.errorMessage()).contains(ForestLoadProperties.FILE_PROPERTY);
        });
    }

    @Test
    @DisplayName("[F-06] 인자 없이 호출하면 설정한 경로의 파일을 적재한다")
    void loadUsesConfiguredFile() {
        // when
        ForestLoadResult result = forestLoadService.load().orElseThrow();

        // then
        assertThat(result.sourceDate()).isEqualTo(FIXTURE_SOURCE_DATE);
        assertThat(result.inserted()).isEqualTo(FOREST_COUNT);
        assertThat(countRows("public_spot_detail WHERE source = 'FOREST'")).isEqualTo(FOREST_COUNT);
    }

    private static Path fixture() throws IOException {
        return new ClassPathResource(FIXTURE).getFile().toPath();
    }

    private int countRows(String tableAndCondition) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableAndCondition, Integer.class);
    }

    private static DetailRow detail(String externalId, String name, String phone, String homepage) {
        return new DetailRow("FOREST", externalId, name, phone, homepage, FIXTURE_SOURCE_DATE.toString());
    }

    private List<DetailRow> detailRows() {
        return jdbcTemplate.query(
                "SELECT d.source, d.external_id, s.name, d.phone, d.homepage, CAST(d.source_date AS CHAR) AS source_date"
                        + " FROM public_spot_detail d JOIN spot s ON s.id = d.spot_id ORDER BY d.external_id",
                (rs, rowNum) -> new DetailRow(
                        rs.getString("source"),
                        rs.getString("external_id"),
                        rs.getString("name"),
                        rs.getString("phone"),
                        rs.getString("homepage"),
                        rs.getString("source_date")));
    }

    private List<Map<String, Object>> spotRows() {
        return jdbcTemplate.queryForList("SELECT id, type, name, ST_AsText(location) AS location, address, weather_nx,"
                + " weather_ny, park_warning, status, created_at, updated_at FROM spot ORDER BY id");
    }

    private List<Map<String, Object>> detailRowsWithTimestamps() {
        return jdbcTemplate.queryForList("SELECT spot_id, source, external_id, phone, homepage,"
                + " CAST(source_date AS CHAR) AS source_date, synced_at, created_at, updated_at"
                + " FROM public_spot_detail ORDER BY spot_id");
    }

    private List<RunRow> runs() {
        return jdbcTemplate.query(
                "SELECT status, progress_cursor, processed_count, skipped_count, error_message, finished_at"
                        + " FROM sync_job_run WHERE job_type = 'FOREST' ORDER BY id",
                (rs, rowNum) -> new RunRow(
                        rs.getString("status"),
                        rs.getString("progress_cursor"),
                        rs.getInt("processed_count"),
                        rs.getInt("skipped_count"),
                        rs.getString("error_message"),
                        rs.getObject("finished_at") != null));
    }

    private record DetailRow(
            String source, String externalId, String name, String phone, String homepage, String sourceDate) {}

    private record RunRow(
            String status,
            String progressCursor,
            int processedCount,
            int skippedCount,
            String errorMessage,
            boolean finished) {}
}
