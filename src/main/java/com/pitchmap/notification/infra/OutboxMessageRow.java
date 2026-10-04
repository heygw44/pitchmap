package com.pitchmap.notification.infra;

import java.time.Instant;

/** 발행기가 읽는 outbox_event 행. MyBatis가 이름으로 매핑하므로, 구성요소 이름은 열 이름을 camelCase로 바꾼 것과 같아야 한다. */
public record OutboxMessageRow(
        long id, String eventType, String aggregateType, long aggregateId, String payload, Instant createdAt) {}
