package com.pitchmap.admin.api;

import com.pitchmap.program.application.ProgramCreateCommand;
import com.pitchmap.program.application.ProgramLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

// 빠진 값을 @NotNull 위반으로 돌려주려고 숫자와 불리언은 래퍼 타입으로 받는다.
// 시각 순서(신청 시작 < 신청 마감 <= 행사 시작 < 행사 종료)와 행사 시작이 현재 이후인지는 서비스가 검사한다.
public record AdminProgramCreateRequest(
        @NotBlank(message = "제목을 입력해야 합니다.") @Size(max = ProgramLimits.TITLE_MAX_LENGTH, message = "제목은 100자 이하여야 합니다.")
        String title,

        @NotBlank(message = "설명을 입력해야 합니다.")
        @Size(max = ProgramLimits.DESCRIPTION_MAX_LENGTH, message = "설명은 5000자 이하여야 합니다.")
        String description,

        @Schema(description = "지도 장소와 연결할 때만 보낸다. 지도에 보이는 장소여야 한다.", example = "12")
        Long spotId,

        @NotBlank(message = "장소 설명을 입력해야 합니다.")
        @Size(max = ProgramLimits.LOCATION_TEXT_MAX_LENGTH, message = "장소 설명은 255자 이하여야 합니다.")
        String locationText,

        @Schema(description = "행사 시작 시각(UTC). 현재보다 늦어야 한다.", example = "2026-11-14T01:00:00Z")
        @NotNull(message = "행사 시작 시각을 입력해야 합니다.")
        Instant startAt,

        @Schema(description = "행사 종료 시각(UTC). 시작 시각보다 늦어야 한다.", example = "2026-11-15T05:00:00Z")
        @NotNull(message = "행사 종료 시각을 입력해야 합니다.")
        Instant endAt,

        @Schema(description = "정원. 1~1000이다.", example = "200")
        @NotNull(message = "정원을 입력해야 합니다.")
        @Min(value = ProgramLimits.CAPACITY_MIN, message = "정원은 1 이상이어야 합니다.")
        @Max(value = ProgramLimits.CAPACITY_MAX, message = "정원은 1000 이하여야 합니다.")
        Integer capacity,

        @Schema(description = "참가비(원). 0~1,000,000이다.", example = "30000")
        @NotNull(message = "참가비를 입력해야 합니다.")
        @Min(value = ProgramLimits.FEE_MIN, message = "참가비는 0 이상이어야 합니다.")
        @Max(value = ProgramLimits.FEE_MAX, message = "참가비는 1000000 이하여야 합니다.")
        Integer fee,

        @Schema(description = "신청 시작 시각(UTC).", example = "2026-11-01T01:00:00Z")
        @NotNull(message = "신청 시작 시각을 입력해야 합니다.")
        Instant applyOpenAt,

        @Schema(description = "신청 마감 시각(UTC). 신청 시작 시각보다 늦고 행사 시작 시각보다 늦지 않아야 한다.", example = "2026-11-10T01:00:00Z")
        @NotNull(message = "신청 마감 시각을 입력해야 합니다.")
        Instant applyCloseAt,

        @Schema(description = "결제 기한(분). 1~1440이고 생략하면 15분이다.", example = "15")
        @Min(value = ProgramLimits.PAYMENT_DEADLINE_MINUTES_MIN, message = "결제 기한은 1분 이상이어야 합니다.")
        @Max(value = ProgramLimits.PAYMENT_DEADLINE_MINUTES_MAX, message = "결제 기한은 1440분 이하여야 합니다.")
        Integer paymentDeadlineMinutes,

        @Schema(description = "숙박 행사 여부.", example = "true") @NotNull(message = "숙박 여부를 입력해야 합니다.")
        Boolean overnight) {

    ProgramCreateCommand toCommand() {
        return new ProgramCreateCommand(
                title,
                description,
                spotId,
                locationText,
                startAt,
                endAt,
                capacity,
                fee,
                applyOpenAt,
                applyCloseAt,
                paymentDeadlineMinutes,
                overnight);
    }
}
