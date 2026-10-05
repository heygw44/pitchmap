package com.pitchmap.publicdata.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.publicdata.domain.SyncJobType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// 여러 스레드가 같은 순간에 실행을 시작하도록 모든 스레드가 준비될 때까지 기다렸다가 한꺼번에 출발시킨다.
// 동시 실행 결과는 실행할 때마다 달라질 수 있어서, 각 경우를 여러 번 반복한다. 반복이 끝날 때마다 공통 확장이 테이블을 비운다.
@IntegrationTest
class SyncJobRunServiceConcurrencyIntegrationTest {

    private static final int THREADS = 8;
    private static final int REPETITIONS = 10;
    private static final Duration STALE_AFTER = Duration.ofMinutes(30);
    private static final long AWAIT_SECONDS = 30;

    @Autowired
    private SyncJobRunService syncJobRunService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    @RepeatedTest(REPETITIONS)
    @DisplayName("[F-06] 실행 기록이 없을 때 같은 종류를 동시에 시작하면 하나만 시작하고 나머지는 예외 없이 빈 값을 받는다")
    void onlyOneStartsWhenNoRunExists() throws Exception {
        // when
        List<Outcome> outcomes = beginConcurrently(Collections.nCopies(THREADS, SyncJobType.GOCAMPING));

        // then
        assertThat(errors(outcomes)).isEmpty();
        assertThat(startedCount(outcomes, SyncJobType.GOCAMPING)).isEqualTo(1);
        assertThat(runningCount(SyncJobType.GOCAMPING)).isEqualTo(1);
        assertThat(totalCount()).isEqualTo(1);
    }

    @RepeatedTest(REPETITIONS)
    @DisplayName("[F-06] 직전 실행이 끝났을 때 같은 종류를 동시에 시작하면 하나만 시작하고 나머지는 예외 없이 빈 값을 받는다")
    void onlyOneStartsWhenLatestRunCompleted() throws Exception {
        // given
        insertRun(SyncJobType.GOCAMPING, "COMPLETED", clock.instant().minus(Duration.ofHours(1)));

        // when
        List<Outcome> outcomes = beginConcurrently(Collections.nCopies(THREADS, SyncJobType.GOCAMPING));

        // then
        assertThat(errors(outcomes)).isEmpty();
        assertThat(startedCount(outcomes, SyncJobType.GOCAMPING)).isEqualTo(1);
        assertThat(runningCount(SyncJobType.GOCAMPING)).isEqualTo(1);
        assertThat(totalCount()).isEqualTo(2);
    }

    @RepeatedTest(REPETITIONS)
    @DisplayName("[F-06] 직전 실행이 오래 갱신되지 않은 RUNNING일 때 동시에 시작하면 하나만 그 기록을 실패로 처리하고 새로 시작한다")
    void onlyOneTakesOverStaleRun() throws Exception {
        // given
        long staleRunId =
                insertRun(SyncJobType.GOCAMPING, "RUNNING", clock.instant().minus(STALE_AFTER.plusMinutes(1)));

        // when
        List<Outcome> outcomes = beginConcurrently(Collections.nCopies(THREADS, SyncJobType.GOCAMPING));

        // then
        assertThat(errors(outcomes)).isEmpty();
        assertThat(startedCount(outcomes, SyncJobType.GOCAMPING)).isEqualTo(1);
        assertThat(runningCount(SyncJobType.GOCAMPING)).isEqualTo(1);
        assertThat(totalCount()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM sync_job_run WHERE id = ?", String.class, staleRunId))
                .isEqualTo("FAILED");
    }

    @RepeatedTest(REPETITIONS)
    @DisplayName("[F-06] 종류가 다른 작업을 동시에 시작하면 서로 막지 않고 종류마다 하나씩 시작한다")
    void differentTypesStartIndependently() throws Exception {
        // given
        List<SyncJobType> jobTypes = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            jobTypes.add(i % 2 == 0 ? SyncJobType.GOCAMPING : SyncJobType.FOREST);
        }

        // when
        List<Outcome> outcomes = beginConcurrently(jobTypes);

        // then
        assertThat(errors(outcomes)).isEmpty();
        assertThat(startedCount(outcomes, SyncJobType.GOCAMPING)).isEqualTo(1);
        assertThat(startedCount(outcomes, SyncJobType.FOREST)).isEqualTo(1);
        assertThat(runningCount(SyncJobType.GOCAMPING)).isEqualTo(1);
        assertThat(runningCount(SyncJobType.FOREST)).isEqualTo(1);
        assertThat(totalCount()).isEqualTo(2);
    }

    private List<Outcome> beginConcurrently(List<SyncJobType> jobTypes) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(jobTypes.size());
        CountDownLatch ready = new CountDownLatch(jobTypes.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Outcome>> futures = new ArrayList<>();
        for (SyncJobType jobType : jobTypes) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return attemptBegin(jobType);
            }));
        }
        ready.await();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        List<Outcome> outcomes = new ArrayList<>();
        for (Future<Outcome> future : futures) {
            outcomes.add(future.get());
        }
        return outcomes;
    }

    // 이 테스트는 시작이 예외 없이 빈 값으로 끝나는지 확인한다. 그래서 어떤 예외가 나도 실패로 세려고 모든 런타임 예외를 결과로 모은다.
    private Outcome attemptBegin(SyncJobType jobType) {
        try {
            return new Outcome(
                    jobType, syncJobRunService.begin(jobType, STALE_AFTER).isPresent(), null);
        } catch (RuntimeException e) {
            return new Outcome(jobType, false, e);
        }
    }

    private static List<String> errors(List<Outcome> outcomes) {
        return outcomes.stream()
                .filter(outcome -> outcome.error() != null)
                .map(outcome -> outcome.error().getClass().getName() + ": "
                        + outcome.error().getMessage())
                .toList();
    }

    private static long startedCount(List<Outcome> outcomes, SyncJobType jobType) {
        return outcomes.stream()
                .filter(outcome -> outcome.jobType() == jobType && outcome.started())
                .count();
    }

    private long insertRun(SyncJobType jobType, String status, Instant updatedAt) {
        jdbcTemplate.update(
                "INSERT INTO sync_job_run (job_type, status, processed_count, started_at, created_at, updated_at)"
                        + " VALUES (?, ?, 0, ?, ?, ?)",
                jobType.name(),
                status,
                updatedAt,
                updatedAt,
                updatedAt);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM sync_job_run", Long.class);
    }

    private int runningCount(SyncJobType jobType) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sync_job_run WHERE job_type = ? AND status = 'RUNNING'",
                Integer.class,
                jobType.name());
    }

    private int totalCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sync_job_run", Integer.class);
    }

    private record Outcome(SyncJobType jobType, boolean started, RuntimeException error) {}
}
