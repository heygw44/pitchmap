package com.pitchmap.common.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEvent, Long> {

    /** 호출하면 그 집합체(예: 회원 한 명)가 기록한 해당 종류의 이벤트 중 주어진 상태인 것이 있는지 알려 준다. */
    boolean existsByEventTypeAndAggregateTypeAndAggregateIdAndStatus(
            String eventType, String aggregateType, long aggregateId, OutboxEventStatus status);
}
