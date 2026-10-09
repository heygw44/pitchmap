package com.pitchmap.program.application;

import com.pitchmap.common.scheduling.ScheduledJobMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 기본값은 1분마다(한국 시간 기준 매분 0초)다. 결제 기한이 15분 안팎이라 만료가 1분 안에 반영되면 충분하다.
// cron을 UTC가 아닌 한국 시간으로 해석해야 하므로 zone을 명시한다.
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "pitchmap.program.payment-expiry",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ProgramPaymentExpiryScheduler {

    private static final String JOB_NAME = "program-payment-expiry";

    private final ProgramPaymentExpiryService expiryService;
    private final ScheduledJobMetrics scheduledJobMetrics;

    public ProgramPaymentExpiryScheduler(
            ProgramPaymentExpiryService expiryService, ScheduledJobMetrics scheduledJobMetrics) {
        this.expiryService = expiryService;
        this.scheduledJobMetrics = scheduledJobMetrics;
        scheduledJobMetrics.register(JOB_NAME);
    }

    @Scheduled(cron = "${pitchmap.program.payment-expiry.cron:0 * * * * *}", zone = "Asia/Seoul")
    public void expire() {
        try {
            expiryService.run();
        } catch (Exception e) {
            // 대상을 읽다가 실패하는 경우처럼 어떤 예외든 이번 실행의 실패 하나로 끝내고 다음 주기에 다시 실행한다.
            // 그래서 작업 이름이 드러나는 ERROR 로그를 직접 남기고 실패 수를 올린다. 예외를 삼키는 곳은 호출 경로의 맨 끝인 여기뿐이다.
            log.error("program payment expiry failed", e);
            scheduledJobMetrics.recordFailure(JOB_NAME);
        }
    }
}
