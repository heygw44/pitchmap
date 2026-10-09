package com.pitchmap.program.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    Payment saveAndFlush(Payment payment);

    Optional<Payment> findByProgramApplicationId(long programApplicationId);

    /** 호출하면 programId인 행사에 낸 신청 중 결제 완료(PAID) 결제가 있는 신청의 ID를 돌려준다. */
    List<Long> findPaidApplicationIdsByProgram(long programId);

    /**
     * 호출하면 programId인 행사에 낸 신청의 결제 중 PAID인 것을 모두 REFUNDED로 바꾸고 환불 시각을 now로 적는다.
     * 바꾼 결제 수를 돌려준다. 이 쿼리로 바꾼 결제의 엔티티는 같은 트랜잭션에서 계속 쓰지 않는다.
     */
    int refundPaidByProgram(long programId, Instant now);
}
