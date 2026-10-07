package com.pitchmap.publicdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.pitchmap.common.scheduling.ScheduledJobMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoCampingSyncSchedulerTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final GoCampingSyncService syncService = mock(GoCampingSyncService.class);
    private final GoCampingSyncScheduler scheduler =
            new GoCampingSyncScheduler(syncService, new ScheduledJobMetrics(meterRegistry));

    @Test
    @DisplayName("[NFR-09] 동기화가 예외로 끝나면 스케줄러는 예외를 삼키고 작업 실패 수를 1 올린다")
    void countsFailureWhenSyncThrows() {
        doThrow(new IllegalStateException("고캠핑 응답 오류")).when(syncService).sync();

        scheduler.sync();

        assertThat(failures()).isEqualTo(1);
    }

    @Test
    @DisplayName("[NFR-09] 스케줄러를 만들면 작업 실패 수가 0으로 먼저 생기고, 동기화가 성공하면 0 그대로다")
    void registersZeroFailuresAndKeepsItOnSuccess() {
        assertThat(failures()).isZero();

        scheduler.sync();

        assertThat(failures()).isZero();
    }

    private double failures() {
        Counter counter = meterRegistry
                .get("pitchmap.scheduled.job.failures")
                .tag("job", "gocamping-sync")
                .counter();
        return counter.count();
    }
}
