package com.pitchmap.trust.application;

import com.pitchmap.common.scheduling.ScheduledJobMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 기본값은 10분마다다. 정지 기간이 끝난 회원은 로그인할 때도 바로 풀리므로, 이 작업은 로그인하지 않은 회원의 상태를 맞추는 용도다.
// cron을 UTC가 아닌 한국 시간으로 해석해야 하므로 zone을 명시한다.
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "pitchmap.trust.sanction-expiry",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SanctionExpiryScheduler {

    private static final String JOB_NAME = "sanction-expiry";

    private final SanctionExpiryService sanctionExpiryService;
    private final ScheduledJobMetrics scheduledJobMetrics;

    public SanctionExpiryScheduler(
            SanctionExpiryService sanctionExpiryService, ScheduledJobMetrics scheduledJobMetrics) {
        this.sanctionExpiryService = sanctionExpiryService;
        this.scheduledJobMetrics = scheduledJobMetrics;
        scheduledJobMetrics.register(JOB_NAME);
    }

    @Scheduled(cron = "${pitchmap.trust.sanction-expiry.cron:0 */10 * * * *}", zone = "Asia/Seoul")
    public void expire() {
        try {
            sanctionExpiryService.run();
        } catch (Exception e) {
            // 어떤 예외든 이번 실행의 실패 하나로 끝내고 다음 주기에 다시 실행한다.
            // 그래서 작업 이름이 드러나는 ERROR 로그를 직접 남기고 실패 수를 올린다. 예외를 삼키는 곳은 호출 경로의 맨 끝인 여기뿐이다.
            log.error("sanction expiry failed", e);
            scheduledJobMetrics.recordFailure(JOB_NAME);
        }
    }
}
