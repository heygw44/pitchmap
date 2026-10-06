package com.pitchmap.notification.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 아웃박스 발행기 설정.
 *
 * @param publisherEnabled false이면 주기적으로 발행하는 스케줄러를 만들지 않는다. 테스트가 발행 시점을 직접 정할 때 쓴다.
 * @param pollDelay 한 번의 발행이 끝난 뒤 다음 발행을 시작하기까지 기다리는 시간
 * @param batchSize 한 번에 집는 이벤트 수의 상한
 * @param lease 발행기가 이벤트를 집은 뒤 다른 발행기가 같은 이벤트를 집지 못하게 막는 시간. 처리기가 이 시간보다 오래 걸리면 다른 발행기가 같은 이벤트를 다시 집을 수 있다.
 * @param retryDelay 처리기가 실패한 이벤트를 다시 집기까지 기다리는 시간. 짧으면 메일 서버가 몇 초 멈추는 것 같은 일시적인 장애 동안 재시도 횟수가 모두 소진된다.
 * @param maxAttempts 이 횟수만큼 실패하면 이벤트를 FAILED로 두고 다시 시도하지 않는다.
 */
@ConfigurationProperties("pitchmap.outbox")
public record OutboxProperties(
        @DefaultValue("true") boolean publisherEnabled,
        @DefaultValue(OutboxProperties.DEFAULT_POLL_DELAY) Duration pollDelay,
        @DefaultValue("50") int batchSize,
        @DefaultValue("5m") Duration lease,
        @DefaultValue("30s") Duration retryDelay,
        @DefaultValue("5") int maxAttempts) {

    /** 스케줄러 어노테이션의 속성 기본값과 같은 값을 쓰려고 상수로 둔다. */
    static final String DEFAULT_POLL_DELAY = "1s";

    public OutboxProperties {
        requirePositive(batchSize, "batchSize");
        requirePositive(maxAttempts, "maxAttempts");
        requirePositive(pollDelay, "pollDelay");
        requirePositive(lease, "lease");
        requirePositive(retryDelay, "retryDelay");
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException("pitchmap.outbox." + name + " must be positive");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("pitchmap.outbox." + name + " must be positive");
        }
    }
}
