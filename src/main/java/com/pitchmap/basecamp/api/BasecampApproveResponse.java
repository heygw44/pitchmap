package com.pitchmap.basecamp.api;

import com.pitchmap.basecamp.application.BasecampApproveResult;

/** 승인한 뒤 베이스캠프의 인원(headcount)과 상태(status)다. 승인해서 정원이 차면 status는 CLOSED다. */
public record BasecampApproveResponse(int headcount, String status) {

    static BasecampApproveResponse from(BasecampApproveResult result) {
        return new BasecampApproveResponse(result.headcount(), result.status());
    }
}
