package com.pitchmap.program.domain;

import java.time.Instant;

public interface PaymentRepository {

    /**
     * 호출하면 programId인 행사에 낸 신청의 결제 중 PAID인 것을 모두 REFUNDED로 바꾸고 환불 시각을 now로 적는다.
     * 바꾼 결제 수를 돌려준다. 이 쿼리로 바꾼 결제의 엔티티는 같은 트랜잭션에서 계속 쓰지 않는다.
     */
    int refundPaidByProgram(long programId, Instant now);
}
