package com.pitchmap.trust.api;

import com.pitchmap.trust.application.IdentityVerifyCommand;
import com.pitchmap.trust.domain.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 빠진 값을 @NotNull 위반으로 돌려주려고 출생연도는 래퍼 타입으로 받는다. 출생연도가 올해를 넘는지는 시각이 필요해서 서비스가 검사한다.
public record IdentityVerificationRequest(
        @Schema(description = "출생연도. 1900년부터 올해(한국 시각)까지.", example = "1995") @NotNull(message = "출생연도를 입력해야 합니다.")
        Integer birthYear,

        @Schema(description = "성별. MALE 또는 FEMALE.") @NotNull(message = "성별을 입력해야 합니다.")
        Gender gender,

        @Schema(description = "시연용 식별 문자열. 같은 값이면 같은 사람으로 본다.", example = "demo-person-1")
        @NotBlank(message = "시연용 식별 문자열을 입력해야 합니다.")
        @Size(max = MAX_DEMO_IDENTITY_KEY_LENGTH, message = "시연용 식별 문자열은 100자 이하여야 합니다.")
        String demoIdentityKey) {

    static final int MAX_DEMO_IDENTITY_KEY_LENGTH = 100;

    IdentityVerifyCommand toCommand() {
        return new IdentityVerifyCommand(birthYear, gender, demoIdentityKey);
    }

    /** 출생연도와 시연용 식별 문자열이 로그에 남지 않게 가린다. */
    @Override
    public String toString() {
        return "IdentityVerificationRequest[gender=" + gender + ", birthYear=****, demoIdentityKey=****]";
    }
}
