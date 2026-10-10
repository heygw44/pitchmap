package com.pitchmap.trust.application;

import com.pitchmap.common.scheduling.ScheduledJobMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 기본값은 매일 새벽 4시 40분(한국 시간)이다. 다른 정리 작업과 시각이 겹치지 않게 10분씩 띄운다.
// cron을 UTC가 아닌 한국 시간으로 해석해야 하므로 zone을 명시한다.
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "pitchmap.trust.ci-retention-cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class CiRetentionCleanupScheduler {

    private static final String JOB_NAME = "ci-retention-cleanup";

    private final CiRetentionCleanupService cleanupService;
    private final ScheduledJobMetrics scheduledJobMetrics;

    public CiRetentionCleanupScheduler(
            CiRetentionCleanupService cleanupService, ScheduledJobMetrics scheduledJobMetrics) {
        this.cleanupService = cleanupService;
        this.scheduledJobMetrics = scheduledJobMetrics;
        scheduledJobMetrics.register(JOB_NAME);
    }

    @Scheduled(cron = "${pitchmap.trust.ci-retention-cleanup.cron:0 40 4 * * *}", zone = "Asia/Seoul")
    public void cleanUp() {
        try {
            cleanupService.deleteRetentionExpired();
        } catch (Exception e) {
            // 어떤 예외든 이번 실행의 실패 하나로 끝내고 다음 주기에 다시 실행한다. 작업 이름이 드러나는 ERROR 로그를 직접 남기고
            // 실패 지표를 올린다. 예외를 삼키는 곳은 호출 경로의 맨 끝인 여기뿐이다.
            log.error("ci retention cleanup failed", e);
            scheduledJobMetrics.recordFailure(JOB_NAME);
        }
    }
}
