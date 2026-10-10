package com.pitchmap.notification.infra;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 발행기가 쓰는 OutboxEventMapper와 달리 트랜잭션 없이 불러도 된다. 삭제 문장 하나가 원자적으로 커밋된다.
// 지울 행 수를 제한하는 삭제 문장이라 JPA 대신 MyBatis를 쓴다.
@Mapper
public interface OutboxEventCleanupMapper {

    /**
     * 호출하면 발행이 끝난(PUBLISHED) 이벤트 중 발행 시각이 cutoff보다 먼저인 행을 최대 limit개 지우고, 지운 행 수를 돌려준다.
     * 아직 발행하지 않은(PENDING) 이벤트와 발행에 실패한(FAILED) 이벤트는 사람이 확인해야 하므로 지우지 않는다.
     */
    int deletePublishedBefore(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
