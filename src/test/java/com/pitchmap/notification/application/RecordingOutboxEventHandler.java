package com.pitchmap.notification.application;

import com.pitchmap.common.outbox.OutboxEventHandler;
import com.pitchmap.common.outbox.OutboxMessage;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 발행기 테스트가 쓰는 처리기. 호출된 이벤트 ID를 기록하고, 지정한 횟수만큼 예외를 던질 수 있다.
 *
 * <p>발행기를 여러 스레드가 동시에 호출하므로 이 클래스는 스레드 안전해야 한다.
 * 통합 테스트는 Spring 컨텍스트를 공유하므로, 각 테스트는 시작할 때 {@link #reset()}을 호출해 이전 테스트의 기록을 지워야 한다.
 */
public class RecordingOutboxEventHandler implements OutboxEventHandler {

    public static final String EVENT_TYPE = "TEST_EVENT";
    public static final String FAILURE_MESSAGE = "simulated handler failure";

    private final List<Long> calledEventIds = new CopyOnWriteArrayList<>();
    private final AtomicInteger remainingFailures = new AtomicInteger();

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public void handle(OutboxMessage message) {
        calledEventIds.add(message.eventId());
        if (shouldFail()) {
            throw new IllegalStateException(FAILURE_MESSAGE);
        }
    }

    /** 호출하면 앞으로 {@code count}번의 {@link #handle} 호출이 예외를 던진다. */
    public void failNextCalls(int count) {
        remainingFailures.set(count);
    }

    /** 실패한 호출을 포함해, {@link #handle}이 호출된 순서대로 이벤트 ID를 돌려준다. */
    public List<Long> calledEventIds() {
        return List.copyOf(calledEventIds);
    }

    public int callCount() {
        return calledEventIds.size();
    }

    public void reset() {
        calledEventIds.clear();
        remainingFailures.set(0);
    }

    private boolean shouldFail() {
        return remainingFailures.getAndUpdate(remaining -> remaining > 0 ? remaining - 1 : 0) > 0;
    }

    /** {@code @IntegrationTest}가 한 번만 가져와 모든 통합 테스트가 같은 컨텍스트 캐시를 쓰게 하는 설정이다. */
    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        RecordingOutboxEventHandler recordingOutboxEventHandler() {
            return new RecordingOutboxEventHandler();
        }
    }
}
