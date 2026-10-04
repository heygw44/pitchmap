package com.pitchmap.notification.application;

import com.pitchmap.common.outbox.OutboxMessage;
import com.pitchmap.notification.infra.OutboxEventMapper;
import com.pitchmap.notification.infra.OutboxMessageRow;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 발행기는 처리기를 호출하는 동안 DB 트랜잭션을 열어 두면 안 된다. 그래서 발행기의 DB 접근은
// 모두 이 클래스의 짧은 트랜잭션 메서드로만 한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventStore {

    private final OutboxEventMapper mapper;
    private final OutboxProperties properties;
    private final Clock clock;

    /**
     * 호출하면 발행할 이벤트를 한 트랜잭션에서 고르고 임대를 건 뒤 돌려준다. 임대는 now에서 {@code lease} 설정 시간이 지날 때까지
     * 다른 발행기가 같은 이벤트를 집지 못하게 막는다.
     */
    @Transactional
    public List<OutboxMessage> claim(Instant now) {
        List<Long> ids = mapper.selectClaimableIds(now, properties.batchSize());
        if (ids.isEmpty()) {
            return List.of();
        }
        mapper.lease(ids, now.plus(properties.lease()), now);
        return mapper.selectByIds(ids).stream().map(OutboxEventStore::toMessage).toList();
    }

    @Transactional
    public void markPublished(long eventId) {
        int updated = mapper.markPublished(eventId, clock.instant());
        if (updated == 0) {
            // 처리기가 임대 시간보다 오래 걸리면 다른 발행기가 같은 이벤트를 다시 집어 먼저 끝냈을 수 있다.
            // 이 경우 처리기가 같은 이벤트를 두 번 받았으므로, 사람이 확인할 수 있게 남긴다.
            log.warn("outbox event was not pending when marking published eventId={}", eventId);
        }
    }

    @Transactional
    public void markFailedAttempt(long eventId, String error) {
        Instant now = clock.instant();
        Instant retryAt = now.plus(properties.retryDelay());
        int updated = mapper.markFailedAttempt(eventId, error, properties.maxAttempts(), retryAt, now);
        if (updated == 0) {
            log.warn("outbox event was not pending when marking failed eventId={}", eventId);
        }
    }

    private static OutboxMessage toMessage(OutboxMessageRow row) {
        return new OutboxMessage(
                row.id(), row.eventType(), row.aggregateType(), row.aggregateId(), row.payload(), row.createdAt());
    }
}
