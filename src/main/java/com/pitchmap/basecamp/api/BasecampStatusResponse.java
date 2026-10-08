package com.pitchmap.basecamp.api;

import com.pitchmap.basecamp.application.BasecampStatusResult;

/** 상태를 바꾸거나 내용을 고친 뒤 베이스캠프의 ID와 상태다. */
public record BasecampStatusResponse(long basecampId, String status) {

    static BasecampStatusResponse from(BasecampStatusResult result) {
        return new BasecampStatusResponse(result.basecampId(), result.status());
    }
}
