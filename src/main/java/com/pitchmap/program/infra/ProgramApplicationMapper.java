package com.pitchmap.program.infra;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 남은 자리를 세면서 한 문장으로 신청을 넣어야 해서 SQL로 직접 쓴다. JPA로는 이 조건부 INSERT를 표현하기 어렵다.
@Mapper
public interface ProgramApplicationMapper {

    /**
     * 호출하면 claim의 행사에 남은 자리(결제 대기·확정 신청 수가 정원보다 적음)가 있을 때만 결제 대기 신청을 넣고 1을 돌려준다.
     * 자리가 없으면 아무것도 넣지 않고 0을 돌려준다. 1을 돌려줄 때 만들어진 신청 ID가 claim의 id에 채워진다.
     * 호출하는 쪽은 먼저 행사 행을 잠가서 같은 행사의 다른 신청이 이 문장과 겹치지 않게 해야 한다.
     */
    int insertIfSeatAvailable(SeatClaim claim);

    /** 호출하면 memberId인 회원이 programId인 행사에 결제 대기 또는 확정 신청을 가졌는지 돌려준다. */
    boolean existsActive(@Param("programId") long programId, @Param("memberId") long memberId);
}
