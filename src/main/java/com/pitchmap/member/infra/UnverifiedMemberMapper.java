package com.pitchmap.member.infra;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 이 삭제는 조건부 문장이라 MyBatis를 쓴다. 대상 ID를 조회한 뒤 삭제하기 전에 회원이 이메일 인증을 마칠 수 있다.
// JPA의 deleteById는 상태를 다시 확인하지 않아 그 회원까지 지운다. 그래서 삭제 문장이 미인증 상태와 가입 시각 조건을
// 한 번 더 걸고, 호출하는 쪽은 영향 행 수로 실제로 지운 수를 안다.
@Mapper
public interface UnverifiedMemberMapper {

    /** 호출하면 cutoff보다 먼저 가입한 미인증 회원 ID를 오래된 순서로 최대 limit개 돌려준다. */
    List<Long> selectExpiredIds(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    /**
     * 호출하면 ids 중 지금도 미인증이고 cutoff보다 먼저 가입한 회원만 지우고, 지운 행 수를 돌려준다.
     * 호출하는 쪽은 ids가 비어 있지 않게 해야 한다.
     */
    int deleteExpiredByIds(@Param("ids") List<Long> ids, @Param("cutoff") Instant cutoff);
}
