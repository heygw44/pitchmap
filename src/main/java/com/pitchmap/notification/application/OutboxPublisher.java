package com.pitchmap.notification.application;

import com.pitchmap.common.outbox.OutboxEventHandler;
import com.pitchmap.common.outbox.OutboxMessage;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 이 클래스에는 @Transactional을 두지 않는다. 처리기가 메일을 보내는 것처럼 오래 걸리는 일을 해도
// DB 커넥션과 행 잠금을 붙잡지 않아야 하기 때문이다. DB 접근은 OutboxEventStore의 짧은 트랜잭션이 맡는다.
@Slf4j
@Service
public class OutboxPublisher {

    private static final int MAX_ERROR_LENGTH = 2000;

    private final OutboxEventStore store;
    private final Clock clock;
    private final Map<String, OutboxEventHandler> handlersByEventType;

    public OutboxPublisher(OutboxEventStore store, Clock clock, List<OutboxEventHandler> handlers) {
        this.store = store;
        this.clock = clock;
        this.handlersByEventType = indexByEventType(handlers);
    }

    /**
     * 호출하면 발행할 이벤트를 집어 이벤트마다 처리기를 호출하고, 성공하면 PUBLISHED로, 실패하면 시도 횟수를 올려 표시한다.
     * 한 이벤트가 실패해도 나머지 이벤트는 계속 처리한다.
     *
     * @return 이번에 집어서 처리한 이벤트 수(실패한 이벤트 포함)
     */
    public int publishPending() {
        List<OutboxMessage> messages = store.claim(clock.instant());
        for (OutboxMessage message : messages) {
            publish(message);
        }
        return messages.size();
    }

    private void publish(OutboxMessage message) {
        Optional<String> failure = handle(message);
        if (failure.isEmpty()) {
            store.markPublished(message.eventId());
            return;
        }
        // 오류 문자열은 처리기가 만든 메시지라 개인정보가 들어 있을 수 있다. 그래서 로그에는 남기지 않고 last_error 열에만 기록한다.
        log.warn("outbox event failed eventId={} eventType={}", message.eventId(), message.eventType());
        store.markFailedAttempt(message.eventId(), failure.get());
    }

    private Optional<String> handle(OutboxMessage message) {
        OutboxEventHandler handler = handlersByEventType.get(message.eventType());
        if (handler == null) {
            return Optional.of(truncate("no handler for " + message.eventType()));
        }
        // 처리기는 모듈마다 다르게 구현하므로 어떤 예외가 나올지 미리 알 수 없다.
        // 그런데 이벤트 하나가 던진 예외가 나머지 이벤트 처리를 막으면 안 되고, 던진 이벤트는 재시도 대상으로 남겨야 한다.
        // 그래서 이 루프에서만 Exception을 통째로 잡아 오류 문자열로 바꾸고, 예외를 삼키지 않고 DB에 기록한다.
        try {
            handler.handle(message);
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of(describe(e));
        }
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        String description = e.getClass().getSimpleName() + (message == null ? "" : ": " + message);
        return truncate(description);
    }

    private static String truncate(String text) {
        return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH);
    }

    private static Map<String, OutboxEventHandler> indexByEventType(List<OutboxEventHandler> handlers) {
        Map<String, OutboxEventHandler> index = new HashMap<>();
        for (OutboxEventHandler handler : handlers) {
            OutboxEventHandler previous = index.put(handler.eventType(), handler);
            if (previous != null) {
                throw new IllegalStateException("duplicate outbox event handler for eventType=" + handler.eventType()
                        + ": " + previous.getClass().getName() + ", "
                        + handler.getClass().getName());
            }
        }
        return Map.copyOf(index);
    }
}
