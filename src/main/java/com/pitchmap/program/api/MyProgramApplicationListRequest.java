package com.pitchmap.program.api;

import com.pitchmap.program.application.MyProgramApplicationQuery;
import com.pitchmap.program.domain.ProgramApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Arrays;

// 상태는 문자열로 받고 허용 값인지 직접 검사한다. 모르는 값을 Spring의 enum 변환 오류가 아니라 필드 오류가 담긴 입력 오류로 돌려주려는 것이다.
// 생략하면 상태로 거르지 않는다.
public record MyProgramApplicationListRequest(
        @Schema(implementation = ProgramApplicationStatus.class, description = "신청 상태. 생략하면 모든 상태.")
        String status,

        @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.") Integer page,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.") @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.")
        Integer size) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    @Schema(hidden = true)
    @AssertTrue(message = "status는 PENDING_PAYMENT, CONFIRMED, CANCELED, EXPIRED 중 하나여야 합니다.")
    public boolean isStatusValid() {
        return status == null
                || Arrays.stream(ProgramApplicationStatus.values())
                        .anyMatch(candidate -> candidate.name().equals(status));
    }

    MyProgramApplicationQuery toQuery() {
        return new MyProgramApplicationQuery(
                status == null ? null : ProgramApplicationStatus.valueOf(status),
                page == null ? DEFAULT_PAGE : page,
                size == null ? DEFAULT_SIZE : size);
    }
}
