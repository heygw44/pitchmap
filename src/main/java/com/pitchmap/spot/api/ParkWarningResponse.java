package com.pitchmap.spot.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pitchmap.spot.application.SpotParkWarning;
import java.time.LocalDate;

/**
 * 장소 상세와 박지 제보 결과의 공원 경계 경고. 경고가 아니면 warned만 내보낸다. 경고인데 공원 경계 행이 없으면 areaName, source,
 * sourceDate도 뺀다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParkWarningResponse(
        boolean warned, String areaName, String source, LocalDate sourceDate, String notice, String guide) {

    static ParkWarningResponse from(SpotParkWarning warning) {
        return new ParkWarningResponse(
                warning.warned(),
                warning.areaName(),
                warning.source(),
                warning.sourceDate(),
                warning.notice(),
                warning.guide());
    }
}
