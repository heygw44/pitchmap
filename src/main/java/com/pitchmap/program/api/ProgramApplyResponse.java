package com.pitchmap.program.api;

import com.pitchmap.program.application.ProgramApplyResult;
import java.time.Instant;

/** 행사 신청 응답이다. 멱등성 기록에 JSON으로 저장했다가 같은 키의 재요청에 그대로 돌려주므로 단순한 값만 둔다. */
public record ProgramApplyResponse(long applicationId, String status, Instant paymentDueAt, int amount) {

    static ProgramApplyResponse from(ProgramApplyResult result) {
        return new ProgramApplyResponse(
                result.applicationId(), result.status(), result.paymentDueAt(), result.amount());
    }
}
