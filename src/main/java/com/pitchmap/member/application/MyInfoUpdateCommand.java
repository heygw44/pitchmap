package com.pitchmap.member.application;

import com.pitchmap.common.web.PatchField;
import java.util.Objects;

/**
 * 내 정보 수정 요청이다. 필드마다 요청에 있었는지와 값을 함께 담는다.
 * 요청에 없던 필드는 바꾸지 않고, 자기 신고 값을 null로 보냈으면 그 값을 지운다.
 */
public record MyInfoUpdateCommand(
        PatchField<String> nickname, PatchField<String> selfAgeGroup, PatchField<String> selfGender) {

    public MyInfoUpdateCommand {
        Objects.requireNonNull(nickname, "nickname");
        Objects.requireNonNull(selfAgeGroup, "selfAgeGroup");
        Objects.requireNonNull(selfGender, "selfGender");
    }
}
