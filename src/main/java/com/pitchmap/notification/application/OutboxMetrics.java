package com.pitchmap.notification.application;

import com.pitchmap.notification.infra.OutboxBacklogRow;
import com.pitchmap.notification.infra.OutboxEventMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 아직 발행하지 못한 아웃박스 이벤트를 운영 지표로 내보낸다.
 *
 * <p>운영자는 가장 오래된 미발행 이벤트의 나이로 발행기가 멈췄는지 알아챈다. 이벤트 수만 보면 일시적으로 몰린 것인지 멈춘 것인지 구분하기 어렵기
 * 때문이다. FAILED 수는 재시도 한도를 넘겨 사람이 확인해야 하는 이벤트 수다.
 *
 * <p>값은 지표를 수집할 때마다 DB에서 읽는다. 수집 주기가 1분이고 조회가 인덱스 구간만 읽어서 부담이 작다.
 */
@Component
@RequiredArgsConstructor
public class OutboxMetrics implements MeterBinder {

    private static final String EVENTS_GAUGE_NAME = "pitchmap.outbox.events";
    private static final String OLDEST_PENDING_AGE_GAUGE_NAME = "pitchmap.outbox.oldest.pending.age";

    private final OutboxEventMapper mapper;
    private final Clock clock;

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(EVENTS_GAUGE_NAME, this, metrics -> metrics.backlog().pendingCount())
                .description("상태별 아웃박스 이벤트 수")
                .tag("status", "pending")
                .register(registry);
        Gauge.builder(EVENTS_GAUGE_NAME, this, metrics -> metrics.backlog().failedCount())
                .description("상태별 아웃박스 이벤트 수")
                .tag("status", "failed")
                .register(registry);
        Gauge.builder(OLDEST_PENDING_AGE_GAUGE_NAME, this, OutboxMetrics::oldestPendingAgeSeconds)
                .description("가장 오래된 미발행 이벤트가 기록된 뒤 지난 시간. 미발행 이벤트가 없으면 0")
                .baseUnit("seconds")
                .register(registry);
    }

    double oldestPendingAgeSeconds() {
        Instant oldest = backlog().oldestPendingCreatedAt();
        if (oldest == null) {
            return 0;
        }
        return Math.max(0, Duration.between(oldest, clock.instant()).toMillis() / 1000.0);
    }

    private OutboxBacklogRow backlog() {
        return mapper.selectBacklog();
    }
}
