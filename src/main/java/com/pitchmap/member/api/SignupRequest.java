package com.pitchmap.member.api;

import com.pitchmap.member.application.SignupCommand;
import com.pitchmap.member.domain.Member;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.", regexp = com.pitchmap.member.domain.Email.DOMAIN_PATTERN)
        @Size(max = Member.EMAIL_MAX_LENGTH, message = "이메일은 " + Member.EMAIL_MAX_LENGTH + "자 이하여야 합니다.")
        String email,

        @NotNull(message = "비밀번호는 필수입니다.") String password,

        @NotBlank(message = "닉네임은 필수입니다.") @ValidNickname String nickname) {

    public SignupCommand toCommand(String requestIp) {
        return new SignupCommand(email, password, nickname, requestIp);
    }

    // 레코드 기본 toString은 모든 구성요소를 찍는다. 그래서 이메일·비밀번호가 로그에 새지 않도록 우리가 재정의했다.
    @Override
    public String toString() {
        return "SignupRequest[nickname=" + nickname + "]";
    }
}
