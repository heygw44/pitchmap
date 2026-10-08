package com.pitchmap.program.domain;

import com.pitchmap.common.web.PatchField;
import java.time.Instant;

/**
 * 행사를 수정할 때 바꿀 내용이다. 값이 null인 필드는 그대로 두고, 장소(spotId)는 요청에 있었는지가 중요해서 {@link PatchField}로 받는다.
 * 장소를 {@code PatchField.of(null)}로 보내면 지도 장소와의 연결을 끊는다.
 */
public record ProgramRevision(
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

    public ProgramRevision {
        spotId = spotId == null ? PatchField.absent() : spotId;
    }
}
