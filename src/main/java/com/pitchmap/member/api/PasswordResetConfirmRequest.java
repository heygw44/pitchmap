package com.pitchmap.member.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 비밀번호 규칙(PW-01)은 서비스가 검사한다. 여기서는 너무 긴 입력을 싸게 거르는 상한만 둔다.
public record PasswordResetConfirmRequest(
        @NotBlank(message = "재설정 토큰은 필수입니다.") @Size(max = 128, message = "재설정 토큰이 올바르지 않습니다.")
        String token,

        @NotBlank(message = "새 비밀번호는 필수입니다.") @Size(max = 128, message = "새 비밀번호가 너무 깁니다.")
        String newPassword) {

    // 레코드 기본 toString은 구성요소를 모두 찍는다. 토큰과 비밀번호가 로그에 새지 않도록 재정의했다.
    @Override
    public String toString() {
        return "PasswordResetConfirmRequest[***]";
    }
}
