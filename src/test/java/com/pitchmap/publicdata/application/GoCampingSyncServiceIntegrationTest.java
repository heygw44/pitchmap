package com.pitchmap.publicdata.application;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.publicdata.infra.GoCampingApiException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

// 테스트 프로필의 페이지 크기는 2다. 픽스처는 전체 5건이라 서비스는 1~3페이지를 차례로 받는다.
// 1페이지: 146, 2329 / 2페이지: 2931, 7001 / 3페이지: 7002
@IntegrationTest
class GoCampingSyncServiceIntegrationTest {

    private static final String BASED_LIST_PATH = "/B551011/GoCamping/basedList";
    private static final String TEST_SERVICE_KEY = "test+key/value==";
    private static final int TOTAL_COUNT = 5;

    @Autowired
    private GoCampingSyncService goCampingSyncService;

    @Autowired
    private WireMockServer wireMock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
    }

    @Test
    @DisplayName("[F-06][NFR-06] 동기화는 모든 페이지를 한 번씩 받아 장소와 상세를 적재하고 실행 기록을 완료로 남긴다")
    void syncLoadsAllPagesAndCompletesRun() {
        // given
        stubAllPages();

        // when
        Optional<GoCampingSyncResult> result = goCampingSyncService.sync();

        // then
        assertThat(result).isPresent();
        assertThat(result.get().pages()).isEqualTo(3);
        assertThat(result.get().processedCount()).isEqualTo(TOTAL_COUNT);
        assertThat(result.get().inserted()).isEqualTo(TOTAL_COUNT);
        assertThat(countRows("spot")).isEqualTo(TOTAL_COUNT);
        assertThat(countRows("public_spot_detail")).isEqualTo(TOTAL_COUNT);
        assertThat(jdbcTemplate.queryForList(
                        "SELECT external_id FROM public_spot_detail WHERE source = 'GOCAMPING' ORDER BY external_id",
                        String.class))
                .containsExactly("146", "2329", "2931", "7001", "7002");
        assertThat(jdbcTemplate.queryForList("SELECT DISTINCT type FROM spot", String.class))
                .containsExactly("CAMPSITE");
        assertThat(runs()).singleElement().satisfies(run -> {
            assertThat(run.status()).isEqualTo("COMPLETED");
            assertThat(run.progressCursor()).isEqualTo("3");
            assertThat(run.processedCount()).isEqualTo(TOTAL_COUNT);
            assertThat(run.finished()).isTrue();
        });
        for (int pageNo = 1; pageNo <= 3; pageNo++) {
            assertThat(requestCount(pageNo)).isEqualTo(1);
        }
        assertThat(requestCount(4)).isZero();
    }

    @Test
    @DisplayName("[F-06] 응답의 mapY를 위도, mapX를 경도로 저장한다")
    void syncStoresMapYAsLatitudeAndMapXAsLongitude() {
        // given
        stubAllPages();

        // when
        goCampingSyncService.sync();

        // then
        Map<String, Object> location = jdbcTemplate.queryForMap(
                "SELECT ST_Latitude(s.location) AS latitude, ST_Longitude(s.location) AS longitude FROM spot s"
                        + " JOIN public_spot_detail d ON d.spot_id = s.id WHERE d.external_id = '146'");
        assertThat(((Number) location.get("latitude")).doubleValue()).isCloseTo(35.4952105728394, within(1e-9));
        assertThat(((Number) location.get("longitude")).doubleValue()).isCloseTo(127.190085215523, within(1e-9));
    }

    @Test
    @DisplayName("[F-06][NFR-06] 중간 페이지에서 실패하면 실행 기록을 실패로 남기고, 다음 실행은 받은 페이지를 다시 부르지 않고 이어서 받는다")
    void syncResumesFromNextPageAfterFailure() {
        // given: 2페이지는 재시도까지 모두 서버 오류로 응답한다.
        stubPage(1);
        stubPage(3);
        wireMock.stubFor(get(urlPathEqualTo(BASED_LIST_PATH))
                .withQueryParam("pageNo", equalTo("2"))
                .willReturn(aResponse().withStatus(500)));

        // when
        assertThatThrownBy(() -> goCampingSyncService.sync()).isInstanceOf(GoCampingApiException.class);

        // then
        RunRow failedRun = runs().getFirst();
        assertThat(failedRun.status()).isEqualTo("FAILED");
        assertThat(failedRun.progressCursor()).isEqualTo("1");
        assertThat(failedRun.processedCount()).isEqualTo(2);
        assertThat(failedRun.errorMessage())
                .isNotBlank()
                .doesNotContain(TEST_SERVICE_KEY)
                .doesNotContain("test%2Bkey")
                .doesNotContain("serviceKey");
        assertThat(countRows("spot")).isEqualTo(2);

        // when: 2페이지가 다시 정상으로 응답한 뒤 재실행한다.
        stubPage(2);
        clock.advance(Duration.ofHours(1));
        goCampingSyncService.sync();

        // then
        List<RunRow> runs = runs();
        assertThat(runs).hasSize(2);
        assertThat(runs.get(0).status()).isEqualTo("FAILED");
        assertThat(runs.get(1).status()).isEqualTo("COMPLETED");
        assertThat(runs.get(1).progressCursor()).isEqualTo("3");
        assertThat(runs.get(1).processedCount()).isEqualTo(3);
        assertThat(requestCount(1)).isEqualTo(1);
        // 첫 실행이 서버 오류로 두 번(처음 호출과 재시도 한 번), 재실행이 한 번 불렀다.
        assertThat(requestCount(2)).isEqualTo(3);
        assertThat(requestCount(3)).isEqualTo(1);
        assertThat(countRows("spot")).isEqualTo(TOTAL_COUNT);
        assertThat(countRows("public_spot_detail")).isEqualTo(TOTAL_COUNT);
    }

    @Test
    @DisplayName("[F-06] 같은 데이터를 두 번 적재해도 행이 늘지 않고, 바뀐 것이 없으면 수정 시각도 그대로다")
    void syncingSameDataTwiceKeepsRowsUnchanged() {
        // given
        stubAllPages();
        goCampingSyncService.sync();
        List<Map<String, Object>> spotsAfterFirst = spotRows();
        List<Map<String, Object>> detailsAfterFirst = detailRows();

        // when
        clock.advance(Duration.ofDays(1));
        Optional<GoCampingSyncResult> second = goCampingSyncService.sync();

        // then
        assertThat(second).isPresent();
        assertThat(second.get().inserted()).isZero();
        assertThat(second.get().updated()).isZero();
        assertThat(second.get().unchanged()).isEqualTo(TOTAL_COUNT);
        assertThat(spotRows()).isEqualTo(spotsAfterFirst);
        assertThat(detailRows()).isEqualTo(detailsAfterFirst);
        assertThat(runs()).hasSize(2).allSatisfy(run -> assertThat(run.status()).isEqualTo("COMPLETED"));
    }

    @Test
    @DisplayName("[F-06] 오래 갱신되지 않은 RUNNING 기록은 실패로 처리하고, 그 기록의 진행 위치 다음 페이지부터 이어서 받는다")
    void syncFailsStaleRunningRunAndResumesFromItsCursor() {
        // given: 서버가 1페이지를 처리한 뒤 꺼져서 31분 동안 갱신되지 않은 기록이다.
        stubAllPages();
        long staleRunId = insertRunningRun(clock.instant().minus(Duration.ofMinutes(31)));

        // when
        Optional<GoCampingSyncResult> result = goCampingSyncService.sync();

        // then
        assertThat(result).isPresent();
        List<RunRow> runs = runs();
        assertThat(runs).hasSize(2);
        assertThat(runs.get(0).id()).isEqualTo(staleRunId);
        assertThat(runs.get(0).status()).isEqualTo("FAILED");
        assertThat(runs.get(0).errorMessage()).isNotBlank();
        assertThat(runs.get(0).finished()).isTrue();
        assertThat(runs.get(1).status()).isEqualTo("COMPLETED");
        assertThat(runs.get(1).progressCursor()).isEqualTo("3");
        assertThat(requestCount(1)).isZero();
        assertThat(requestCount(2)).isEqualTo(1);
        assertThat(countRows("spot")).isEqualTo(3);
    }

    @Test
    @DisplayName("[F-06][NFR-06] 최근에 갱신된 RUNNING 기록이 있으면 다른 실행이 진행 중이므로 API를 부르지 않고 건너뛴다")
    void syncSkipsWhenAnotherRunIsInProgress() {
        // given
        stubAllPages();
        insertRunningRun(clock.instant().minus(Duration.ofMinutes(5)));

        // when
        Optional<GoCampingSyncResult> result = goCampingSyncService.sync();

        // then
        assertThat(result).isEmpty();
        wireMock.verify(0, anyRequestedFor(anyUrl()));
        assertThat(runs())
                .singleElement()
                .satisfies(run -> assertThat(run.status()).isEqualTo("RUNNING"));
    }

    private void stubAllPages() {
        for (int pageNo = 1; pageNo <= 3; pageNo++) {
            stubPage(pageNo);
        }
    }

    private void stubPage(int pageNo) {
        wireMock.stubFor(get(urlPathEqualTo(BASED_LIST_PATH))
                .withQueryParam("pageNo", equalTo(String.valueOf(pageNo)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json;charset=UTF-8")
                        .withBody(readFixture(pageNo))));
    }

    private static String readFixture(int pageNo) {
        String path = "fixtures/publicdata/gocamping/sync/based-list-page" + pageNo + ".json";
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private int requestCount(int pageNo) {
        return wireMock.countRequestsMatching(getRequestedFor(urlPathEqualTo(BASED_LIST_PATH))
                        .withQueryParam("pageNo", equalTo(String.valueOf(pageNo)))
                        .build())
                .getCount();
    }

    private int countRows(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private List<Map<String, Object>> spotRows() {
        return jdbcTemplate.queryForList("SELECT id, type, name, ST_AsText(location) AS location, address, weather_nx,"
                + " weather_ny, park_warning, status, created_at, updated_at FROM spot ORDER BY id");
    }

    private List<Map<String, Object>> detailRows() {
        return jdbcTemplate.queryForList("SELECT spot_id, source, external_id, category, CAST(facilities AS CHAR)"
                + " AS facilities, phone, homepage, synced_at, created_at, updated_at FROM public_spot_detail"
                + " ORDER BY spot_id");
    }

    private long insertRunningRun(Instant updatedAt) {
        jdbcTemplate.update(
                "INSERT INTO sync_job_run (job_type, status, progress_cursor, processed_count, started_at,"
                        + " created_at, updated_at) VALUES ('GOCAMPING', 'RUNNING', '1', 2, ?, ?, ?)",
                updatedAt,
                updatedAt,
                updatedAt);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM sync_job_run", Long.class);
    }

    private List<RunRow> runs() {
        return jdbcTemplate.query(
                "SELECT id, status, progress_cursor, processed_count, error_message, finished_at FROM sync_job_run"
                        + " WHERE job_type = 'GOCAMPING' ORDER BY id",
                (rs, rowNum) -> new RunRow(
                        rs.getLong("id"),
                        rs.getString("status"),
                        rs.getString("progress_cursor"),
                        rs.getInt("processed_count"),
                        rs.getString("error_message"),
                        rs.getObject("finished_at") != null));
    }

    private record RunRow(
            long id, String status, String progressCursor, int processedCount, String errorMessage, boolean finished) {}
}
