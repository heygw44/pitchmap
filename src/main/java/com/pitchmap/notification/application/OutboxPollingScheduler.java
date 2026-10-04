package com.pitchmap.notification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// fixedDelay는 이전 발행이 끝난 뒤부터 간격을 센다. 그래서 같은 인스턴스에서 발행이 겹치지 않는다.
// 인스턴스가 여러 대여도 OutboxEventStore가 SKIP LOCKED로 행을 나눠 집으므로 겹쳐 실행해도 된다.
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "pitchmap.outbox",
        name = "publisher-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OutboxPollingScheduler {

    private final OutboxPublisher publisher;

    @Scheduled(fixedDelayString = "${pitchmap.outbox.poll-delay:" + OutboxProperties.DEFAULT_POLL_DELAY + "}")
    public void poll() {
        int processed = publisher.publishPending();
        if (processed > 0) {
            log.debug("outbox events processed count={}", processed);
        }
    }
}
