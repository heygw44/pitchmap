package com.pitchmap.member.api;

import com.pitchmap.member.domain.Member;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.", regexp = com.pitchmap.member.domain.Email.DOMAIN_PATTERN)
        @Size(max = Member.EMAIL_MAX_LENGTH, message = "이메일은 " + Member.EMAIL_MAX_LENGTH + "자 이하여야 합니다.")
        String email) {

    // 레코드 기본 toString은 구성요소를 모두 찍는다. 이메일이 로그에 새지 않도록 재정의했다.
    @Override
    public String toString() {
        return "PasswordResetRequest[***]";
    }
}
