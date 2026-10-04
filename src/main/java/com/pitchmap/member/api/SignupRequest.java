package com.pitchmap.member.api;

import com.pitchmap.member.application.SignupCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 254, message = "이메일은 254자 이하여야 합니다.")
        String email,

        @NotNull(message = "비밀번호는 필수입니다.") String password,

        @NotBlank(message = "닉네임은 필수입니다.") @Size(min = 2, max = 20, message = "닉네임은 2~20자여야 합니다.")
        String nickname) {

    public SignupCommand toCommand(String requestIp) {
        return new SignupCommand(email, password, nickname, requestIp);
    }

    // 레코드 기본 toString은 모든 구성요소를 찍는다. 그래서 이메일·비밀번호가 로그에 새지 않도록 우리가 재정의했다.
    @Override
    public String toString() {
        return "SignupRequest[nickname=" + nickname + "]";
    }
}
