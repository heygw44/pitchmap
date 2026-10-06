package com.pitchmap.spot.application;

import com.pitchmap.common.web.PatchField;
import java.util.Objects;

/**
 * 박지 수정 요청이다. 필드마다 요청에 있었는지와 값을 함께 담는다. 요청에 없던 필드는 바꾸지 않는다.
 * 설명, 통신 상태, 바닥 유형을 null로 보냈으면 그 값을 지운다. 이름, 좌표, 물·화장실 유무는 지울 수 없어서 서비스가 null을 거부한다.
 */
public record BakjiUpdateCommand(
        PatchField<String> name,
        PatchField<Double> lat,
        PatchField<Double> lng,
        PatchField<String> description,
        PatchField<Boolean> hasWater,
        PatchField<Boolean> hasToilet,
        PatchField<String> signalLevel,
        PatchField<String> groundType) {

    public BakjiUpdateCommand {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(lat, "lat");
        Objects.requireNonNull(lng, "lng");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(hasWater, "hasWater");
        Objects.requireNonNull(hasToilet, "hasToilet");
        Objects.requireNonNull(signalLevel, "signalLevel");
        Objects.requireNonNull(groundType, "groundType");
    }
}
