package com.pitchmap.basecamp.api;

import com.pitchmap.basecamp.application.BasecampOpenResult;

public record BasecampOpenResponse(long basecampId, String status) {

    static BasecampOpenResponse from(BasecampOpenResult result) {
        return new BasecampOpenResponse(result.basecampId(), result.status());
    }
}
