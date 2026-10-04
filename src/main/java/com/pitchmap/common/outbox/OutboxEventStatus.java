package com.pitchmap.common.outbox;

/** 이벤트의 발행 상태. PENDING에서 PUBLISHED로 가거나, 재시도 한도를 넘으면 FAILED로 간다. */
public enum OutboxEventStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
