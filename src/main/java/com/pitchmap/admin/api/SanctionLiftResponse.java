package com.pitchmap.admin.api;

import com.pitchmap.trust.application.SanctionLiftResult;

/** 제재 해제의 응답. status는 해제 뒤의 제재 상태다. */
public record SanctionLiftResponse(long sanctionId, String status) {

    static SanctionLiftResponse from(SanctionLiftResult result) {
        return new SanctionLiftResponse(result.sanctionId(), result.status());
    }
}
