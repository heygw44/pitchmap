package com.pitchmap.publicdata.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 고캠핑 동기화 작업 설정.
 *
 * @param enabled false이면 정해진 시각에 동기화하는 스케줄러를 만들지 않는다. 테스트가 동기화 시점을 직접 정할 때 쓴다.
 * @param cron 스케줄러가 동기화를 시작하는 시각. 한국 시간으로 해석한다.
 * @param staleAfter 실행 중인 기록이 이 시간보다 오래 갱신되지 않으면, 다음 실행은 서버가 작업 도중 꺼진 것으로 보고 그 기록을 실패로 처리한다.
 *     작업은 페이지 하나를 끝낼 때마다 기록을 갱신하므로, 이 값은 페이지 하나를 처리하는 시간보다 충분히 길어야 한다.
 */
@ConfigurationProperties("pitchmap.publicdata.gocamping.sync")
public record GoCampingSyncProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("0 0 3 * * *") String cron,
        @DefaultValue("30m") Duration staleAfter) {

    public GoCampingSyncProperties {
        if (cron == null || cron.isBlank()) {
            throw new IllegalArgumentException("pitchmap.publicdata.gocamping.sync.cron must not be blank");
        }
        if (staleAfter.isZero() || staleAfter.isNegative()) {
            throw new IllegalArgumentException("pitchmap.publicdata.gocamping.sync.staleAfter must be positive");
        }
    }
}
