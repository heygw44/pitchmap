package com.pitchmap.trust.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.pitchmap.common.scheduling.ScheduledJobMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CiRetentionCleanupSchedulerTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ScheduledJobMetrics metrics = new ScheduledJobMetrics(meterRegistry);

    @Test
    @DisplayName("[NFR-09][PV-05] CI 보관 기한 정리가 예외로 끝나면 스케줄러는 예외를 삼키고 작업 실패 수를 1 올린다")
    void countsCleanupFailure() {
        CiRetentionCleanupService service = mock(CiRetentionCleanupService.class);
        doThrow(new IllegalStateException("DB 연결 실패")).when(service).deleteRetentionExpired();

        new CiRetentionCleanupScheduler(service, metrics).cleanUp();

        assertThat(meterRegistry
                        .get("pitchmap.scheduled.job.failures")
                        .tag("job", "ci-retention-cleanup")
                        .counter()
                        .count())
                .isEqualTo(1);
    }
}
