package com.pitchmap.member.application;

import com.pitchmap.common.web.PatchField;
import com.pitchmap.member.domain.Member;
import java.util.Objects;

/**
 * 내 정보 수정 요청이다. 필드마다 요청에 있었는지와 값을 함께 담는다.
 * 요청에 없던 필드는 바꾸지 않고, 자기 신고 값을 null로 보냈으면 그 값을 지운다.
 */
public record MyInfoUpdateCommand(
        PatchField<String> nickname, PatchField<String> selfAgeGroup, PatchField<String> selfGender) {

    /**
     * 닉네임 길이 규칙이다. 값의 원본은 회원 도메인의 {@link Member}에 있다. 다른 모듈의 요청 DTO는 회원 모듈의 domain 패키지에
     * 접근할 수 없으므로, API 문서가 같은 값을 쓸 수 있게 application 계층에서 다시 공개한다.
     */
    public static final int NICKNAME_MIN_LENGTH = Member.NICKNAME_MIN_LENGTH;

    public static final int NICKNAME_MAX_LENGTH = Member.NICKNAME_MAX_LENGTH;

    public MyInfoUpdateCommand {
        Objects.requireNonNull(nickname, "nickname");
        Objects.requireNonNull(selfAgeGroup, "selfAgeGroup");
        Objects.requireNonNull(selfGender, "selfGender");
    }
}
