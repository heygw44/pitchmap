package com.pitchmap.member.application;

import com.pitchmap.common.scheduling.ScheduledJobMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 기본값은 매일 새벽 4시(한국 시간)다. 사용자가 적은 시간에 돌려 가입 요청과 겹치지 않게 한다.
// cron을 UTC가 아닌 한국 시간으로 해석해야 하므로 zone을 명시한다.
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "pitchmap.member.unverified-cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class UnverifiedMemberCleanupScheduler {

    private static final String JOB_NAME = "unverified-member-cleanup";

    private final UnverifiedMemberCleanupService cleanupService;
    private final ScheduledJobMetrics scheduledJobMetrics;

    public UnverifiedMemberCleanupScheduler(
            UnverifiedMemberCleanupService cleanupService, ScheduledJobMetrics scheduledJobMetrics) {
        this.cleanupService = cleanupService;
        this.scheduledJobMetrics = scheduledJobMetrics;
        scheduledJobMetrics.register(JOB_NAME);
    }

    @Scheduled(cron = "${pitchmap.member.unverified-cleanup.cron:0 0 4 * * *}", zone = "Asia/Seoul")
    public void cleanUp() {
        try {
            cleanupService.deleteExpiredUnverifiedMembers();
        } catch (Exception e) {
            // 어떤 예외든 이번 실행의 실패 하나로 끝내고 다음 주기에 다시 실행한다. 예외를 스케줄러 기본 처리기에 맡기면
            // 어느 작업이 실패했는지 로그만 봐서는 알기 어렵다. 그래서 작업 이름이 드러나는 ERROR 로그를 직접 남긴다.
            // 예외를 삼키는 곳은 호출 경로의 맨 끝인 여기뿐이다.
            log.error("unverified member cleanup failed", e);
            scheduledJobMetrics.recordFailure(JOB_NAME);
        }
    }
}
