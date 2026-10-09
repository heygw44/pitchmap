package com.pitchmap.program.infra;

import java.time.Instant;
import lombok.Getter;

/**
 * 자리 선점 INSERT에 넘길 값과, INSERT가 만든 신청 ID를 받는 그릇이다.
 * MyBatis가 생성된 ID를 {@code id} 필드에 채워야 해서 record가 아니라 클래스로 둔다.
 */
@Getter
public class SeatClaim {

    private final long programId;
    private final long memberId;
    private final Instant paymentDueAt;
    private final Instant now;
    private Long id;

    public SeatClaim(long programId, long memberId, Instant paymentDueAt, Instant now) {
        this.programId = programId;
        this.memberId = memberId;
        this.paymentDueAt = paymentDueAt;
        this.now = now;
    }
}
