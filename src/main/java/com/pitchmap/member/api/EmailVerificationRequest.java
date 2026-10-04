package com.pitchmap.member.api;

import jakarta.validation.constraints.NotBlank;

// 코드 형식(숫자 6자리)은 여기서 검사하지 않는다. 서비스가 형식이 틀린 코드를 EMAIL_CODE_INVALID로 거부하기 때문이다.
public record EmailVerificationRequest(
        @NotBlank(message = "인증 코드는 필수입니다.") String code) {

    // 레코드 기본 toString은 모든 구성요소를 찍는다. 그래서 인증 코드가 로그에 새지 않도록 우리가 재정의했다.
    @Override
    public String toString() {
        return "EmailVerificationRequest[***]";
    }
}
