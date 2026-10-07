package com.pitchmap.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.pitchmap.common.scheduling.ScheduledJobMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberCleanupSchedulerMetricsTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ScheduledJobMetrics metrics = new ScheduledJobMetrics(meterRegistry);

    @Test
    @DisplayName("[NFR-09] 미인증 회원 정리가 예외로 끝나면 스케줄러는 예외를 삼키고 작업 실패 수를 1 올린다")
    void countsUnverifiedMemberCleanupFailure() {
        UnverifiedMemberCleanupService service = mock(UnverifiedMemberCleanupService.class);
        doThrow(new IllegalStateException("DB 연결 실패")).when(service).deleteExpiredUnverifiedMembers();

        new UnverifiedMemberCleanupScheduler(service, metrics).cleanUp();

        assertThat(failures("unverified-member-cleanup")).isEqualTo(1);
    }

    @Test
    @DisplayName("[NFR-09] 재설정 요청 기록 정리가 예외로 끝나면 스케줄러는 예외를 삼키고 작업 실패 수를 1 올린다")
    void countsPasswordResetThrottleCleanupFailure() {
        PasswordResetThrottleCleanupService service = mock(PasswordResetThrottleCleanupService.class);
        doThrow(new IllegalStateException("DB 연결 실패")).when(service).deleteStaleThrottles();

        new PasswordResetThrottleCleanupScheduler(service, metrics).cleanUp();

        assertThat(failures("password-reset-throttle-cleanup")).isEqualTo(1);
    }

    private double failures(String job) {
        return meterRegistry
                .get("pitchmap.scheduled.job.failures")
                .tag("job", job)
                .counter()
                .count();
    }
}
