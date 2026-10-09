package com.pitchmap.notification.application;

import com.pitchmap.common.scheduling.ScheduledJobMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 기본값은 매일 새벽 5시(한국 시간)다. 다른 정리 작업이 모두 끝난 뒤에 돌게 한다.
// cron을 UTC가 아닌 한국 시간으로 해석해야 하므로 zone을 명시한다.
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "pitchmap.outbox.cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OutboxEventCleanupScheduler {

    private static final String JOB_NAME = "outbox-event-cleanup";

    private final OutboxEventCleaner cleaner;
    private final ScheduledJobMetrics scheduledJobMetrics;

    public OutboxEventCleanupScheduler(OutboxEventCleaner cleaner, ScheduledJobMetrics scheduledJobMetrics) {
        this.cleaner = cleaner;
        this.scheduledJobMetrics = scheduledJobMetrics;
        scheduledJobMetrics.register(JOB_NAME);
    }

    @Scheduled(cron = "${pitchmap.outbox.cleanup.cron:0 0 5 * * *}", zone = "Asia/Seoul")
    public void cleanUp() {
        try {
            cleaner.deletePublished();
        } catch (Exception e) {
            // 어떤 예외든 이번 실행의 실패 하나로 끝내고 다음 주기에 다시 실행한다. 작업 이름이 드러나는 ERROR 로그를 직접 남기고
            // 실패 지표를 올린다. 예외를 삼키는 곳은 호출 경로의 맨 끝인 여기뿐이다.
            log.error("outbox event cleanup failed", e);
            scheduledJobMetrics.recordFailure(JOB_NAME);
        }
    }
}
