package com.pitchmap.program.api;

import com.pitchmap.program.application.ProgramPayResult;
import java.time.Instant;

/** 결제 응답이다. 멱등성 기록에 JSON으로 저장했다가 같은 키의 재요청에 그대로 돌려주므로 단순한 값만 둔다. */
public record ProgramPayResponse(long applicationId, String status, Instant paidAt) {

    static ProgramPayResponse from(ProgramPayResult result) {
        return new ProgramPayResponse(result.applicationId(), result.status(), result.paidAt());
    }
}
