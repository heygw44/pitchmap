package com.pitchmap.basecamp.api;

import com.pitchmap.basecamp.application.BasecampApplyResult;

public record BasecampApplyResponse(long applicationId, String status) {

    static BasecampApplyResponse from(BasecampApplyResult result) {
        return new BasecampApplyResponse(result.applicationId(), result.status());
    }
}
