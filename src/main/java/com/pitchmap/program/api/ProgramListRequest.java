package com.pitchmap.program.api;

import com.pitchmap.program.application.ProgramListQuery;
import com.pitchmap.program.domain.ProgramPhase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

// 단계는 문자열로 받고 허용 값인지 직접 검사한다. 모르는 값을 Spring의 enum 변환 오류가 아니라 필드 오류가 담긴 입력 오류로 돌려주려는 것이다.
// 취소된 행사는 목록에서 빼므로 CANCELED는 허용하지 않는다. 생략하면 단계로 거르지 않는다.
public record ProgramListRequest(
        @Schema(
                description = "진행 단계. 생략하면 취소되지 않은 행사 모두.",
                allowableValues = {"UPCOMING", "OPEN", "CLOSED"})
        String status,

        @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.") Integer page,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.") @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.")
        Integer size) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    @Schema(hidden = true)
    @AssertTrue(message = "status는 UPCOMING, OPEN, CLOSED 중 하나여야 합니다.")
    public boolean isStatusValid() {
        return status == null
                || status.equals(ProgramPhase.UPCOMING.name())
                || status.equals(ProgramPhase.OPEN.name())
                || status.equals(ProgramPhase.CLOSED.name());
    }

    ProgramListQuery toQuery() {
        ProgramPhase phase = status == null ? null : ProgramPhase.valueOf(status);
        return new ProgramListQuery(phase, page == null ? DEFAULT_PAGE : page, size == null ? DEFAULT_SIZE : size);
    }
}
