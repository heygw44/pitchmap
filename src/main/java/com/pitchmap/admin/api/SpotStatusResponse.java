package com.pitchmap.admin.api;

import com.pitchmap.spot.application.SpotStatusResult;

/** 장소 숨김·복구의 응답. status는 처리 뒤의 장소 상태다. */
public record SpotStatusResponse(long spotId, String status) {

    static SpotStatusResponse from(SpotStatusResult result) {
        return new SpotStatusResponse(result.spotId(), result.status());
    }
}
