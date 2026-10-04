package com.pitchmap.common.outbox;

import java.time.Instant;

/**
 * 발행기가 {@link OutboxEventHandler}에 넘기는 이벤트 값이다.
 *
 * @param eventId 이벤트 행의 ID. 처리기는 이 값으로 중복 처리를 가려낼 수 있다.
 * @param payload 기록할 때 JSON으로 바꾼 문자열. 처리기가 필요한 타입으로 읽는다.
 * @param occurredAt 이벤트를 기록한 시각(행의 created_at)
 */
public record OutboxMessage(
        long eventId, String eventType, String aggregateType, long aggregateId, String payload, Instant occurredAt) {}
