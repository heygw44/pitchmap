package com.pitchmap.spot.api;

import com.pitchmap.spot.application.BakjiReportCommand;
import com.pitchmap.spot.domain.BakjiContent;
import com.pitchmap.spot.domain.BakjiGroundType;
import com.pitchmap.spot.domain.BakjiSignalLevel;
import com.pitchmap.spot.domain.Spot;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 통신 상태와 바닥 유형은 문자열로 받고 서비스가 허용 값인지 검증한다. 모르는 값을 Jackson의 enum 변환 오류가 아니라 서비스의 입력 오류로 돌려주려는 것이다.
@InWeatherGrid
public record BakjiCreateRequest(
        @NotBlank(message = "이름을 입력해야 합니다.") @Size(max = Spot.NAME_MAX_LENGTH, message = "이름은 100자 이하여야 합니다.")
        String name,

        @NotNull(message = "위도를 입력해야 합니다.")
        @DecimalMin(value = "-90", message = "위도는 -90 이상이어야 합니다.")
        @DecimalMax(value = "90", message = "위도는 90 이하여야 합니다.")
        Double lat,

        @NotNull(message = "경도를 입력해야 합니다.")
        @DecimalMin(value = "-180", message = "경도는 -180 이상이어야 합니다.")
        @DecimalMax(value = "180", message = "경도는 180 이하여야 합니다.")
        Double lng,

        @Size(max = BakjiContent.DESCRIPTION_MAX_LENGTH, message = "설명은 2000자 이하여야 합니다.")
        String description,

        @NotNull(message = "물 유무를 입력해야 합니다.") Boolean hasWater,
        @NotNull(message = "화장실 유무를 입력해야 합니다.") Boolean hasToilet,

        @Schema(implementation = BakjiSignalLevel.class, description = "통신 상태. 모르면 생략한다.")
        String signalLevel,

        @Schema(implementation = BakjiGroundType.class, description = "바닥 유형. 모르면 생략한다.")
        String groundType) {

    BakjiReportCommand toCommand() {
        return new BakjiReportCommand(name, lat, lng, description, hasWater, hasToilet, signalLevel, groundType);
    }
}
