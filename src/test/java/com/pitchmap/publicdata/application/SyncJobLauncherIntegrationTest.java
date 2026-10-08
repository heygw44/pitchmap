package com.pitchmap.publicdata.application;

import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.publicdata.domain.PublicDataErrorCode;
import com.pitchmap.publicdata.domain.SyncJobType;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// 테스트 설정(application-test.yml)이 휴양림과 공원 경계 픽스처 파일을 적재 경로로 지정해 둔다.
// 실행기는 작업을 다른 스레드에서 돌리므로, 테스트는 실행 기록이 끝날 때까지 짧은 간격으로 다시 읽으며 기다린다.
// 테스트가 끝나면 공통 확장이 테이블을 비우므로, 각 테스트는 시작한 작업이 끝난 것을 확인한 뒤에 끝낸다.
@IntegrationTest
class SyncJobLauncherIntegrationTest {

    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(50);
    private static final int FIXTURE_FOREST_COUNT = 3;

    @Autowired
    private SyncJobLauncher syncJobLauncher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    @Autowired
    private MemberJpaRepository memberRepository;

    private long adminId;

    @BeforeEach
    void setUp() {
        adminId = memberRepository.saveAndFlush(aMember().build()).getId();
    }

    @Test
    @DisplayName("[F-06] 휴양림 적재를 시작하면 실행 기록 ID를 바로 돌려주고, 작업이 끝나면 그 기록이 COMPLETED가 된다")
    void launchedForestLoadCompletes() throws InterruptedException {
        // when
        long runId = syncJobLauncher.launch(SyncJobType.FOREST, adminId);

        // then
        assertThat(awaitFinished(runId)).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject("SELECT job_type FROM sync_job_run WHERE id = ?", String.class, runId))
                .isEqualTo("FOREST");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT processed_count FROM sync_job_run WHERE id = ?", Integer.class, runId))
                .isEqualTo(FIXTURE_FOREST_COUNT);
        assertThat(countRows("public_spot_detail WHERE source = 'FOREST'")).isEqualTo(FIXTURE_FOREST_COUNT);
    }

    @Test
    @DisplayName("[F-06] 공원 경계 적재를 시작하면 실행 기록 ID를 바로 돌려주고, 작업이 끝나면 그 기록이 COMPLETED가 된다")
    void launchedParkBoundaryLoadCompletes() throws InterruptedException {
        // when
        long runId = syncJobLauncher.launch(SyncJobType.PARK_BOUNDARY, adminId);

        // then
        assertThat(awaitFinished(runId)).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject("SELECT job_type FROM sync_job_run WHERE id = ?", String.class, runId))
                .isEqualTo("PARK_BOUNDARY");
        assertThat(countRows("protected_area")).isPositive();
    }

    @Test
    @DisplayName("[F-06] 시작한 작업이 실패하면 실행 기록이 FAILED가 되고 오류 메시지가 남는다")
    void launchedJobFailureIsRecorded() throws InterruptedException {
        // given: WireMock에 스텁이 없어서 고캠핑 API 호출이 실패한다.

        // when
        long runId = syncJobLauncher.launch(SyncJobType.GOCAMPING, adminId);

        // then
        assertThat(awaitFinished(runId)).isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT error_message FROM sync_job_run WHERE id = ?", String.class, runId))
                .isNotBlank();
    }

    @Test
    @DisplayName("[F-06] 같은 종류의 작업이 실행 중이면 SYNC_JOB_ALREADY_RUNNING을 던지고 실행 기록을 만들지 않는다")
    void launchWhileRunningIsRejected() {
        // given
        insertRunningRun(SyncJobType.FOREST, clock.instant());

        // when, then
        assertThatThrownBy(() -> syncJobLauncher.launch(SyncJobType.FOREST, adminId))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(PublicDataErrorCode.SYNC_JOB_ALREADY_RUNNING));
        assertThat(countRows("sync_job_run")).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-21] 관리자가 작업을 시작하면 같은 요청의 감사 로그가 한 줄 남고, 실행 중이라 거부된 요청은 남기지 않는다")
    void adminLaunchIsAudited() throws InterruptedException {
        // when
        long runId = syncJobLauncher.launch(SyncJobType.FOREST, adminId);
        awaitFinished(runId);

        // then
        assertThat(countRows("admin_audit_log")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT CONCAT(admin_id, ':', action, ':', target_type, ':', target_id,"
                                + " ':', JSON_UNQUOTE(JSON_EXTRACT(detail, '$.jobType'))) FROM admin_audit_log",
                        String.class))
                .isEqualTo(adminId + ":SYNC_JOB_RUN:SYNC_JOB_RUN:" + runId + ":FOREST");
    }

    @Test
    @DisplayName("[F-21] 이미 같은 종류가 실행 중이라 거부된 관리자 요청은 감사 로그를 남기지 않는다")
    void rejectedLaunchIsNotAudited() {
        // given
        insertRunningRun(SyncJobType.FOREST, clock.instant());

        // when
        assertThatThrownBy(() -> syncJobLauncher.launch(SyncJobType.FOREST, adminId))
                .isInstanceOf(BusinessException.class);

        // then
        assertThat(countRows("admin_audit_log")).isZero();
    }

    @Test
    @DisplayName("[F-06] 박지 재판정을 시작하면 작업이 끝난 뒤 실행 기록이 COMPLETED가 되고, 경계 안 박지에 공원 경고가 붙는다")
    void launchedBakjiRejudgeCompletes() throws InterruptedException {
        // given
        insertProtectedArea("북한산", "MULTIPOLYGON(((126.9 37.6, 127.0 37.6, 127.0 37.7, 126.9 37.7, 126.9 37.6)))");
        long areaId = protectedAreaIdOf("북한산");
        long spotId = insertBakji("경계 안 박지", 37.65, 126.95);

        // when
        long runId = syncJobLauncher.launch(SyncJobType.BAKJI_REJUDGE, adminId);

        // then
        assertThat(awaitFinished(runId)).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject("SELECT job_type FROM sync_job_run WHERE id = ?", String.class, runId))
                .isEqualTo("BAKJI_REJUDGE");
        assertParkWarning(spotId, areaId);
    }

    @Test
    @DisplayName("[F-06] 공원 경계 적재가 성공하면 이어서 박지 재판정 실행 기록이 생겨 COMPLETED가 되고, 새 경계 안 박지에 공원 경고가 붙는다")
    void parkBoundaryLoadChainsBakjiRejudge() throws InterruptedException {
        // given: 설정한 픽스처의 북한산 경계는 경도 126.9~127.0, 위도 37.6~37.7이다.
        long spotId = insertBakji("북한산 안 박지", 37.65, 126.95);

        // when
        long runId = syncJobLauncher.launch(SyncJobType.PARK_BOUNDARY, adminId);

        // then: 재판정은 적재 기록이 COMPLETED가 된 뒤에 시작하므로, 재판정 기록이 생길 때까지 따로 기다린다.
        assertThat(awaitFinished(runId)).isEqualTo("COMPLETED");
        long rejudgeRunId = awaitRunOf(SyncJobType.BAKJI_REJUDGE);
        assertThat(awaitFinished(rejudgeRunId)).isEqualTo("COMPLETED");
        assertParkWarning(spotId, protectedAreaIdOf("북한산"));
    }

    private long awaitRunOf(SyncJobType jobType) throws InterruptedException {
        long deadline = System.nanoTime() + AWAIT_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            Long runId =
                    jdbcTemplate
                            .queryForList("SELECT id FROM sync_job_run WHERE job_type = ?", Long.class, jobType.name())
                            .stream()
                            .findFirst()
                            .orElse(null);
            if (runId != null) {
                return runId;
            }
            Thread.sleep(POLL_INTERVAL);
        }
        throw new AssertionError(jobType + " 실행 기록이 " + AWAIT_TIMEOUT + " 안에 생기지 않았다.");
    }

    private void assertParkWarning(long spotId, long protectedAreaId) {
        Map<String, Object> row =
                jdbcTemplate.queryForMap("SELECT park_warning, protected_area_id FROM spot WHERE id = ?", spotId);
        assertThat(row.get("park_warning")).isEqualTo(true);
        assertThat(((Number) row.get("protected_area_id")).longValue()).isEqualTo(protectedAreaId);
    }

    private void insertProtectedArea(String name, String multiPolygonWkt) {
        jdbcTemplate.update(
                "INSERT INTO protected_area (name, area_type, source, source_date, boundary, created_at, updated_at)"
                        + " VALUES (?, 'NATIONAL_PARK', 'KDPA', '2025-12-31',"
                        + " ST_GeomFromText(?, 4326, 'axis-order=long-lat'), NOW(6), NOW(6))",
                name,
                multiPolygonWkt);
    }

    private long protectedAreaIdOf(String name) {
        return jdbcTemplate.queryForObject("SELECT id FROM protected_area WHERE name = ?", Long.class, name);
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

    private String awaitFinished(long runId) throws InterruptedException {
        long deadline = System.nanoTime() + AWAIT_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            String status =
                    jdbcTemplate.queryForObject("SELECT status FROM sync_job_run WHERE id = ?", String.class, runId);
            if (!"RUNNING".equals(status)) {
                return status;
            }
            Thread.sleep(POLL_INTERVAL);
        }
        throw new AssertionError("실행 기록이 " + AWAIT_TIMEOUT + " 안에 끝나지 않았다. runId=" + runId);
    }

    private void insertRunningRun(SyncJobType jobType, Instant updatedAt) {
        jdbcTemplate.update(
                "INSERT INTO sync_job_run (job_type, status, processed_count, started_at, created_at, updated_at)"
                        + " VALUES (?, 'RUNNING', 0, ?, ?, ?)",
                jobType.name(),
                updatedAt,
                updatedAt,
                updatedAt);
    }

    private int countRows(String tableAndCondition) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableAndCondition, Integer.class);
    }
}
