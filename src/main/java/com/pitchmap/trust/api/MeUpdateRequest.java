package com.pitchmap.trust.api;

import com.pitchmap.common.web.PatchField;
import com.pitchmap.member.application.MyInfoUpdateCommand;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 내 정보 수정 요청이다. 모든 필드가 선택이고, 필드를 빼는 것과 {@code null}을 보내는 것의 뜻이 다르다.
 * 서버는 요청에 없는 필드를 그대로 두고, {@code null}로 보낸 자기 신고 값은 지운다. 닉네임은 지울 수 없어서 서버가 {@code null}을 거부한다.
 *
 * <p>값의 형식(닉네임 길이, 허용된 연령대·성별 값)은 회원 모듈의 서비스가 검증한다. 이 요청은 받은 값을 그대로 넘긴다.
 *
 * <p>JSON에 빠진 필드를 Jackson이 Java {@code null}로 넘기는 경우가 있다. 그래서 생성자가 {@code null}을 "요청에 없음"으로 바꾼다.
 */
public record MeUpdateRequest(
        @Schema(
                implementation = String.class,
                minLength = MyInfoUpdateCommand.NICKNAME_MIN_LENGTH,
                maxLength = MyInfoUpdateCommand.NICKNAME_MAX_LENGTH,
                description = "앞뒤 공백, 연속 공백, 보이지 않는 문자는 쓸 수 없다. null로 보내면 400이다.")
        PatchField<String> nickname,

        @Schema(
                implementation = String.class,
                description = "자기 신고 연령대. null로 보내면 지운다.",
                allowableValues = {"TWENTIES", "THIRTIES", "FORTIES", "FIFTIES", "SIXTIES_PLUS"})
        PatchField<String> selfAgeGroup,

        @Schema(
                implementation = String.class,
                description = "자기 신고 성별. null로 보내면 지운다.",
                allowableValues = {"FEMALE", "MALE"})
        PatchField<String> selfGender) {

    public MeUpdateRequest {
        nickname = absentIfNull(nickname);
        selfAgeGroup = absentIfNull(selfAgeGroup);
        selfGender = absentIfNull(selfGender);
    }

    public MyInfoUpdateCommand toCommand() {
        return new MyInfoUpdateCommand(nickname, selfAgeGroup, selfGender);
    }

    private static <T> PatchField<T> absentIfNull(PatchField<T> field) {
        return field == null ? PatchField.absent() : field;
    }
}
