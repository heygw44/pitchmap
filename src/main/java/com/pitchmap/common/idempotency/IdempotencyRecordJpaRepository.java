package com.pitchmap.common.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

// 기본 메서드는 호출마다 짧은 트랜잭션을 따로 연다. 선점 행이 곧바로 커밋돼야 하므로 @Transactional 메서드를 더하지 않는다.
public interface IdempotencyRecordJpaRepository extends JpaRepository<IdempotencyRecord, IdempotencyRecordId> {}
