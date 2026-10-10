package com.pitchmap.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.pitchmap.common.scheduling.ScheduledJobMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdempotencyRecordCleanupSchedulerTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ScheduledJobMetrics metrics = new ScheduledJobMetrics(meterRegistry);

    @Test
    @DisplayName("[NFR-09] 멱등성 기록 정리가 예외로 끝나면 스케줄러는 예외를 삼키고 작업 실패 수를 1 올린다")
    void countsCleanupFailure() {
        IdempotencyRecordCleaner cleaner = mock(IdempotencyRecordCleaner.class);
        doThrow(new IllegalStateException("DB 연결 실패")).when(cleaner).deleteExpired();

        new IdempotencyRecordCleanupScheduler(cleaner, metrics).cleanUp();

        assertThat(meterRegistry
                        .get("pitchmap.scheduled.job.failures")
                        .tag("job", "idempotency-record-cleanup")
                        .counter()
                        .count())
                .isEqualTo(1);
    }
}
