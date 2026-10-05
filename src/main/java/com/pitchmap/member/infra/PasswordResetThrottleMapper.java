package com.pitchmap.member.infra;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 행을 만드는 일은 "없을 때만 넣기"라는 조건부 문장이라 MyBatis를 쓴다. JPA로 먼저 조회하고 저장하면
// 같은 키의 첫 요청 둘이 모두 행이 없다고 보고 둘 다 넣으려다 한쪽이 기본 키 충돌로 실패한다.
// 삭제도 지울 행 수를 제한하는 문장이라 같은 이유로 여기에 둔다.
@Mapper
public interface PasswordResetThrottleMapper {

    /**
     * 호출하면 그 키의 행이 없을 때 요청 0번인 행을 만든다. 이미 있으면 아무것도 바꾸지 않는다.
     * 같은 키로 동시에 호출해도 기본 키 충돌 없이 모두 성공한다.
     */
    int insertIfAbsent(@Param("key") String key, @Param("now") Instant now);

    /** 호출하면 마지막 요청이 cutoff보다 먼저인 행을 최대 limit개 지우고, 지운 행 수를 돌려준다. */
    int deleteStale(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
