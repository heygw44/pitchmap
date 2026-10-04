package com.pitchmap.common.outbox;

import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 다른 모듈로 넘길 이벤트를 {@code outbox_event}에 PENDING으로 기록한다.
 *
 * <p>호출하는 쪽의 {@code application} 서비스는 원래 작업과 같은 트랜잭션 안에서 이 메서드를 불러야 한다.
 * 그래야 원래 작업이 롤백될 때 이벤트도 함께 사라진다. 이 클래스는 {@code common}에 두었으므로 트랜잭션을 직접 열지 않고,
 * 열려 있지 않으면 예외로 알린다.
 */
@Component
@RequiredArgsConstructor
public class OutboxEventRecorder {

    private final OutboxEventJpaRepository outboxEventRepository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public void record(String eventType, String aggregateType, long aggregateId, Object payload) {
        requireActiveTransaction();
        String payloadJson = toJson(payload);
        outboxEventRepository.save(
                OutboxEvent.pending(eventType, aggregateType, aggregateId, payloadJson, Instant.now(clock)));
    }

    // 트랜잭션 밖에서 저장하면 이벤트만 따로 커밋된다. 그러면 원래 작업이 실패해도 이벤트가 남아 후속 처리가 실행된다.
    private void requireActiveTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("outbox 이벤트는 원래 작업과 같은 트랜잭션 안에서 기록해야 합니다.");
        }
    }

    // payload에는 회원 정보가 들어갈 수 있으므로 예외 메시지에 값을 싣지 않는다.
    private String toJson(Object payload) {
        if (payload == null) {
            throw new IllegalArgumentException("이벤트 payload가 null입니다.");
        }
        try {
            return jsonMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            throw new IllegalArgumentException(
                    "이벤트 payload를 JSON으로 바꿀 수 없습니다. payload 타입="
                            + payload.getClass().getName(),
                    e);
        }
    }
}
