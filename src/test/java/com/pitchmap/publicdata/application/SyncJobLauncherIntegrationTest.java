package com.pitchmap.publicdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.publicdata.domain.PublicDataErrorCode;
import com.pitchmap.publicdata.domain.SyncJobType;
import java.time.Duration;
import java.time.Instant;
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

    @Test
    @DisplayName("[F-06] 휴양림 적재를 시작하면 실행 기록 ID를 바로 돌려주고, 작업이 끝나면 그 기록이 COMPLETED가 된다")
    void launchedForestLoadCompletes() throws InterruptedException {
        // when
        long runId = syncJobLauncher.launch(SyncJobType.FOREST);

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
        long runId = syncJobLauncher.launch(SyncJobType.PARK_BOUNDARY);

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
        long runId = syncJobLauncher.launch(SyncJobType.GOCAMPING);

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
        assertThatThrownBy(() -> syncJobLauncher.launch(SyncJobType.FOREST))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(PublicDataErrorCode.SYNC_JOB_ALREADY_RUNNING));
        assertThat(countRows("sync_job_run")).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-06] 박지 재판정은 아직 실행할 수 없어 INVALID_INPUT을 던지고 실행 기록을 만들지 않는다")
    void bakjiRejudgeIsRejected() {
        // when, then
        assertThatThrownBy(() -> syncJobLauncher.launch(SyncJobType.BAKJI_REJUDGE))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
        assertThat(countRows("sync_job_run")).isZero();
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
