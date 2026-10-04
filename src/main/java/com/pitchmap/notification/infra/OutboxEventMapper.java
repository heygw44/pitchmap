package com.pitchmap.notification.infra;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 이벤트를 기록하는 쪽은 JPA를 쓰지만, 발행기는 SQL을 직접 정해야 해서 MyBatis를 쓴다.
// 여러 발행기가 같은 행을 집지 않도록 FOR UPDATE SKIP LOCKED 잠금 힌트가 필요하다.
// 또한 상태가 PENDING인 행만 바꾸는 조건부 UPDATE로 갱신하고, 영향받은 행 수를 호출하는 쪽에 돌려줘야 한다.
// 이 매퍼의 모든 메서드는 호출하는 쪽(OutboxEventStore)이 연 트랜잭션 안에서 실행해야 한다.
@Mapper
public interface OutboxEventMapper {

    /**
     * 호출하면 PENDING이면서 임대가 없거나 이미 끝난 행의 ID를 오래된 순으로 최대 limit개 고르고, 그 행을 잠근다.
     * 다른 트랜잭션이 이미 잠근 행은 기다리지 않고 건너뛴다. 잠금은 호출한 트랜잭션이 끝날 때까지 유지된다.
     */
    List<Long> selectClaimableIds(@Param("now") Instant now, @Param("limit") int limit);

    /** 호출하면 ids의 행에 임대 만료 시각을 기록하고 갱신된 행 수를 돌려준다. ids가 비어 있으면 호출하지 않는다. */
    int lease(@Param("ids") List<Long> ids, @Param("lockedUntil") Instant lockedUntil, @Param("now") Instant now);

    /** 호출하면 ids의 행을 ID 순으로 읽는다. ids가 비어 있으면 호출하지 않는다. */
    List<OutboxMessageRow> selectByIds(@Param("ids") List<Long> ids);

    /** 호출하면 PENDING인 행만 PUBLISHED로 바꾸고 임대를 푼다. 이미 다른 상태이면 0을 돌려준다. */
    int markPublished(@Param("id") long id, @Param("now") Instant now);

    /**
     * 호출하면 PENDING인 행의 시도 횟수를 1 올리고 오류를 기록한다. 임대 만료 시각은 retryAt으로 바꿔서, 그 시각 전에는 다시 집히지 않게 한다.
     * 올린 시도 횟수가 maxAttempts 이상이면 상태를 FAILED로 바꾼다. 이미 다른 상태이면 0을 돌려준다.
     */
    int markFailedAttempt(
            @Param("id") long id,
            @Param("error") String error,
            @Param("maxAttempts") int maxAttempts,
            @Param("retryAt") Instant retryAt,
            @Param("now") Instant now);
}
