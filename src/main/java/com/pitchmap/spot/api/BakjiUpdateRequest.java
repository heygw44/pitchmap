package com.pitchmap.spot.api;

import com.pitchmap.common.web.PatchField;
import com.pitchmap.spot.application.BakjiUpdateCommand;
import com.pitchmap.spot.domain.BakjiContent;
import com.pitchmap.spot.domain.Spot;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 박지 수정 요청이다. 모든 필드가 선택이고, 필드를 빼는 것과 {@code null}을 보내는 것의 뜻이 다르다.
 * 서버는 요청에 없는 필드를 그대로 두고, {@code null}로 보낸 설명·통신 상태·바닥 유형은 지운다.
 * 이름, 위도·경도, 물·화장실 유무는 지울 수 없어서 서버가 {@code null}을 거부한다. 위도와 경도는 함께 보내야 한다.
 *
 * <p>값의 형식(이름·설명 길이, 좌표 범위, 허용된 통신 상태·바닥 유형)은 서비스가 검증한다. 이 요청은 받은 값을 그대로 넘긴다.
 *
 * <p>JSON에 빠진 필드를 Jackson이 Java {@code null}로 넘기는 경우가 있다. 그래서 생성자가 {@code null}을 "요청에 없음"으로 바꾼다.
 */
public record BakjiUpdateRequest(
        @Schema(
                implementation = String.class,
                maxLength = Spot.NAME_MAX_LENGTH,
                description = "공백뿐인 값은 쓸 수 없다. null로 보내면 400이다.")
        PatchField<String> name,

        @Schema(
                implementation = Double.class,
                minimum = "-90",
                maximum = "90",
                description = "lng와 함께 보내야 한다. null로 보내면 400이다.")
        PatchField<Double> lat,

        @Schema(
                implementation = Double.class,
                minimum = "-180",
                maximum = "180",
                description = "lat와 함께 보내야 한다. null로 보내면 400이다.")
        PatchField<Double> lng,

        @Schema(
                implementation = String.class,
                maxLength = BakjiContent.DESCRIPTION_MAX_LENGTH,
                description = "null로 보내면 지운다.")
        PatchField<String> description,

        @Schema(implementation = Boolean.class, description = "null로 보내면 400이다.")
        PatchField<Boolean> hasWater,

        @Schema(implementation = Boolean.class, description = "null로 보내면 400이다.")
        PatchField<Boolean> hasToilet,

        @Schema(
                implementation = String.class,
                description = "통신 상태. null로 보내면 지운다.",
                allowableValues = {"NONE", "WEAK", "GOOD"})
        PatchField<String> signalLevel,

        @Schema(
                implementation = String.class,
                description = "바닥 유형. null로 보내면 지운다.",
                allowableValues = {"SOIL", "GRASS", "GRAVEL", "SAND", "ROCK", "DECK"})
        PatchField<String> groundType) {

    public BakjiUpdateRequest {
        name = absentIfNull(name);
        lat = absentIfNull(lat);
        lng = absentIfNull(lng);
        description = absentIfNull(description);
        hasWater = absentIfNull(hasWater);
        hasToilet = absentIfNull(hasToilet);
        signalLevel = absentIfNull(signalLevel);
        groundType = absentIfNull(groundType);
    }

    BakjiUpdateCommand toCommand() {
        return new BakjiUpdateCommand(name, lat, lng, description, hasWater, hasToilet, signalLevel, groundType);
    }

    private static <T> PatchField<T> absentIfNull(PatchField<T> field) {
        return field == null ? PatchField.absent() : field;
    }
}
