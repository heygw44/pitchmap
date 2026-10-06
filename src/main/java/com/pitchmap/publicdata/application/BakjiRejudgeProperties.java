package com.pitchmap.publicdata.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 박지 재판정 설정.
 *
 * @param staleAfter 실행 중인 기록이 이 시간보다 오래 갱신되지 않으면, 다음 실행은 서버가 작업 도중 꺼진 것으로 보고 그 기록을 실패로 처리한다.
 *     재판정은 모든 박지를 한 번에 처리한 뒤에야 기록을 갱신하므로, 이 값은 재판정 한 번에 걸리는 시간보다 충분히 길어야 한다.
 */
@ConfigurationProperties("pitchmap.publicdata.bakji-rejudge")
public record BakjiRejudgeProperties(@DefaultValue("30m") Duration staleAfter) {

    public BakjiRejudgeProperties {
        if (staleAfter.isZero() || staleAfter.isNegative()) {
            throw new IllegalArgumentException("pitchmap.publicdata.bakji-rejudge.staleAfter must be positive");
        }
    }
}
