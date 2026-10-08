package com.pitchmap.program.domain;

import java.time.Instant;

public interface ProgramApplicationRepository {

    /**
     * 호출하면 programId인 행사의 결제 대기·확정 신청을 모두 CANCELED로 바꾸고, 취소 사유를 reason, 취소 시각을 now로 적는다.
     * 바꾼 신청 수를 돌려준다. 호출하는 쪽은 먼저 행사 행을 잠가서 이 사이에 새 신청이 들어오지 못하게 해야 한다.
     * 이 쿼리로 바꾼 신청의 엔티티는 같은 트랜잭션에서 계속 쓰지 않는다.
     */
    int cancelActiveByProgram(long programId, ProgramCancelReason reason, Instant now);
}
