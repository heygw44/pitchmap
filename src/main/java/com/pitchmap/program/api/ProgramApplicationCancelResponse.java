package com.pitchmap.program.api;

import com.pitchmap.program.application.ProgramApplicationCancelResult;

/** 신청 취소 응답이다. refunded는 결제를 환불했는지 여부다. */
public record ProgramApplicationCancelResponse(String status, boolean refunded) {

    static ProgramApplicationCancelResponse from(ProgramApplicationCancelResult result) {
        return new ProgramApplicationCancelResponse(result.status(), result.refunded());
    }
}
