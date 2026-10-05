package com.pitchmap.member.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 254, message = "이메일은 254자 이하여야 합니다.")
        String email) {

    // 레코드 기본 toString은 구성요소를 모두 찍는다. 이메일이 로그에 새지 않도록 재정의했다.
    @Override
    public String toString() {
        return "PasswordResetRequest[***]";
    }
}
