package com.pitchmap.program.infra;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 결제 만료는 결제, 본인 취소, 행사 취소, 다른 서버의 만료와 겹쳐도 한 번만 일어나야 한다. 그래서 엔티티를 읽고 고치지 않고,
// 상태와 기한 조건을 WHERE에 건 UPDATE를 직접 쓴다. 바뀐 열은 도메인 엔티티가 만료·취소할 때 바꾸는 열과 같아야 한다.
@Mapper
public interface ProgramPaymentExpiryMapper {

    /** 호출하면 결제 기한이 now 이하인 결제 대기 신청을 afterId 뒤부터 ID 오름차순으로 최대 limit개 읽는다. */
    List<ExpiryTarget> selectDueTargets(
            @Param("now") Instant now, @Param("afterId") long afterId, @Param("limit") int limit);

    /** 호출하면 결제 기한이 now 이하인 결제 대기 신청 하나를 만료로 바꾸고 바뀐 행 수를 돌려준다. 이미 다른 상태이면 0이다. */
    int expireIfDue(@Param("id") long id, @Param("now") Instant now);
}
