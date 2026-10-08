package com.pitchmap.program.application;

import com.pitchmap.common.web.PatchField;
import java.time.Instant;
import java.util.Objects;

/**
 * 관리자가 행사를 수정하는 요청 내용이다. 값이 null인 필드는 바꾸지 않는다.
 * 장소(spotId)는 요청에 있었는지가 중요해서 {@link PatchField}로 받고, null 값으로 보내면 지도 장소와의 연결을 끊는다.
 */
public record ProgramReviseCommand(
        String title,
        String description,
        PatchField<Long> spotId,
        String locationText,
        Instant startAt,
        Instant endAt,
        Integer capacity,
        Integer fee,
        Instant applyOpenAt,
        Instant applyCloseAt,
        Integer paymentDeadlineMinutes,
        Boolean overnight) {

    public ProgramReviseCommand {
        Objects.requireNonNull(spotId, "spotId");
    }
}
