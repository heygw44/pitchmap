package com.pitchmap.publicdata.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.publicdata.domain.SyncJobType;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

// 픽스처의 경계는 테스트에서 지어낸 작은 사각형이다. 좌표는 (경도 위도) 순서다.
// parks_20251231.tsv: 북한산 경도 126.9~127.0, 위도 37.6~37.7
// parks_20260630.tsv: 북한산을 경도 127.1~127.2로 옮겼다. 적재 서비스는 같은 공원 행의 경계를 덮어쓰므로 ID는 그대로다.
@IntegrationTest
class BakjiRejudgeServiceIntegrationTest {

    private static final String FIXTURE = "fixtures/publicdata/park-boundary/parks_20251231.tsv";
    private static final String NEWER_FIXTURE = "fixtures/publicdata/park-boundary/parks_20260630.tsv";

    @Autowired
    private BakjiRejudgeService bakjiRejudgeService;

    @Autowired
    private ParkBoundaryLoadService parkBoundaryLoadService;

    @Autowired
    private SyncJobRunService syncJobRunService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("[F-06] 공원 경계를 바꾼 뒤 재판정하면 박지의 공원 경고가 새 경계와 일치한다")
    void rejudgeMatchesNewBoundaries() throws IOException {
        // given
        parkBoundaryLoadService.load(fixture(FIXTURE)).orElseThrow();
        long bukhansanId = idOf("북한산");
        long oldInside = insertBakji("옛 경계 안 박지", 37.65, 126.95);
        long newInside = insertBakji("새 경계 안 박지", 37.65, 127.15);
        long outside = insertBakji("경계 밖 박지", 35.0, 129.0);

        // when
        BakjiRejudgeResult first = bakjiRejudgeService.rejudge().orElseThrow();

        // then
        assertThat(first.judgedCount()).isEqualTo(3);
        assertWarned(oldInside, bukhansanId);
        assertNotWarned(newInside);
        assertNotWarned(outside);

        // when: 북한산 경계를 옮긴 파일을 적재하고 다시 판정한다.
        parkBoundaryLoadService.load(fixture(NEWER_FIXTURE)).orElseThrow();
        bakjiRejudgeService.rejudge().orElseThrow();

        // then
        assertThat(idOf("북한산")).isEqualTo(bukhansanId);
        assertNotWarned(oldInside);
        assertWarned(newInside, bukhansanId);
        assertNotWarned(outside);
    }

    @Test
    @DisplayName("[F-06] 재판정이 끝나면 실행 기록이 COMPLETED가 되고 처리 건수가 박지 수와 같다")
    void rejudgeCompletesRunWithBakjiCount() throws IOException {
        // given
        parkBoundaryLoadService.load(fixture(FIXTURE)).orElseThrow();
        insertBakji("박지 하나", 37.65, 126.95);
        insertBakji("박지 둘", 35.0, 129.0);

        // when
        bakjiRejudgeService.rejudge().orElseThrow();

        // then
        Map<String, Object> run = jdbcTemplate.queryForMap(
                "SELECT status, processed_count FROM sync_job_run WHERE job_type = 'BAKJI_REJUDGE'");
        assertThat(run.get("status")).isEqualTo("COMPLETED");
        assertThat(((Number) run.get("processed_count")).intValue()).isEqualTo(2);
    }

    @Test
    @DisplayName("[F-06] 다른 박지 재판정이 실행 중이면 시작하지 않고 빈 값을 돌려준다")
    void rejudgeReturnsEmptyWhileAnotherRunIsInProgress() {
        // given
        syncJobRunService
                .begin(SyncJobType.BAKJI_REJUDGE, Duration.ofMinutes(30))
                .orElseThrow();

        // when, then
        assertThat(bakjiRejudgeService.rejudge()).isEmpty();
        assertThat(jdbcTemplate.queryForList(
                        "SELECT status FROM sync_job_run WHERE job_type = 'BAKJI_REJUDGE'", String.class))
                .containsExactly("RUNNING");
    }

    private void assertWarned(long spotId, long protectedAreaId) {
        Map<String, Object> row = judgement(spotId);
        assertThat(row.get("park_warning")).isEqualTo(true);
        assertThat(((Number) row.get("protected_area_id")).longValue()).isEqualTo(protectedAreaId);
        assertThat(row.get("area_checked_at")).isNotNull();
    }

    private void assertNotWarned(long spotId) {
        Map<String, Object> row = judgement(spotId);
        assertThat(row.get("park_warning")).isEqualTo(false);
        assertThat(row.get("protected_area_id")).isNull();
        assertThat(row.get("area_checked_at")).isNotNull();
    }

    private Map<String, Object> judgement(long spotId) {
        return jdbcTemplate.queryForMap(
                "SELECT park_warning, protected_area_id, area_checked_at FROM spot WHERE id = ?", spotId);
    }

    // POINT는 (경도, 위도) 순서로 받는다.
    private long insertBakji(String name, double latitude, double longitude) {
        jdbcTemplate.update(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                        + " VALUES ('BAKJI', ?, ST_SRID(POINT(?, ?), 4326), 60, 127, 'ACTIVE', NOW(6), NOW(6))",
                name,
                longitude,
                latitude);
        return jdbcTemplate.queryForObject("SELECT id FROM spot WHERE name = ?", Long.class, name);
    }

    private long idOf(String name) {
        return jdbcTemplate.queryForObject("SELECT id FROM protected_area WHERE name = ?", Long.class, name);
    }

    private static Path fixture(String path) throws IOException {
        return new ClassPathResource(path).getFile().toPath();
    }
}
