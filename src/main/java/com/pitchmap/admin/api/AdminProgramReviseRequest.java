package com.pitchmap.admin.api;

import com.pitchmap.common.web.PatchField;
import com.pitchmap.program.application.ProgramLimits;
import com.pitchmap.program.application.ProgramReviseCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * 행사 수정 요청이다. 모든 필드가 선택이고, 보내지 않은 필드는 그대로 둔다.
 * 장소(spotId)는 필드를 빼는 것과 {@code null}을 보내는 것의 뜻이 다르다. 서버는 {@code null}로 보내면 지도 장소와의 연결을 끊는다.
 *
 * <p>각 값의 범위는 여기서 검사하고, 시각 순서와 정원 축소 가능 여부는 서비스가 바뀐 뒤의 값으로 검사한다.
 * JSON에 빠진 필드를 Jackson이 Java {@code null}로 넘기는 경우가 있다. 그래서 생성자가 spotId의 {@code null}을 "요청에 없음"으로 바꾼다.
 */
public record AdminProgramReviseRequest(
        @Size(max = ProgramLimits.TITLE_MAX_LENGTH, message = "제목은 100자 이하여야 합니다.")
        @Pattern(regexp = ".*\\S.*", message = "제목은 공백뿐일 수 없습니다.")
        String title,

        @Size(max = ProgramLimits.DESCRIPTION_MAX_LENGTH, message = "설명은 5000자 이하여야 합니다.")
        @Pattern(regexp = "(?s).*\\S.*", message = "설명은 공백뿐일 수 없습니다.")
        String description,

        @Schema(implementation = Long.class, description = "null로 보내면 지도 장소와의 연결을 끊는다. 값이 있으면 지도에 보이는 장소여야 한다.")
        PatchField<Long> spotId,

        @Size(max = ProgramLimits.LOCATION_TEXT_MAX_LENGTH, message = "장소 설명은 255자 이하여야 합니다.")
        @Pattern(regexp = ".*\\S.*", message = "장소 설명은 공백뿐일 수 없습니다.")
        String locationText,

        Instant startAt,
        Instant endAt,

        @Schema(description = "정원. 1~1000이고, 신청이 시작된 뒤에는 줄일 수 없다.", example = "250")
        @Min(value = ProgramLimits.CAPACITY_MIN, message = "정원은 1 이상이어야 합니다.")
        @Max(value = ProgramLimits.CAPACITY_MAX, message = "정원은 1000 이하여야 합니다.")
        Integer capacity,

        @Min(value = ProgramLimits.FEE_MIN, message = "참가비는 0 이상이어야 합니다.")
        @Max(value = ProgramLimits.FEE_MAX, message = "참가비는 1000000 이하여야 합니다.")
        Integer fee,

        Instant applyOpenAt,
        Instant applyCloseAt,

        @Min(value = ProgramLimits.PAYMENT_DEADLINE_MINUTES_MIN, message = "결제 기한은 1분 이상이어야 합니다.")
        @Max(value = ProgramLimits.PAYMENT_DEADLINE_MINUTES_MAX, message = "결제 기한은 1440분 이하여야 합니다.")
        Integer paymentDeadlineMinutes,

        Boolean overnight) {

    public AdminProgramReviseRequest {
        spotId = spotId == null ? PatchField.absent() : spotId;
    }

    @Schema(hidden = true)
    @AssertTrue(message = "고칠 필드를 하나 이상 보내야 합니다.")
    public boolean isAnyFieldPresent() {
        return title != null
                || description != null
                || spotId.present()
                || locationText != null
                || startAt != null
                || endAt != null
                || capacity != null
                || fee != null
                || applyOpenAt != null
                || applyCloseAt != null
                || paymentDeadlineMinutes != null
                || overnight != null;
    }

    ProgramReviseCommand toCommand() {
        return new ProgramReviseCommand(
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
