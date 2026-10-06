package com.pitchmap.trust.application;

import com.pitchmap.trust.domain.Gender;

/** 본인확인 요청 값이다. 출생연도와 시연용 식별 문자열이 로그에 남지 않게 toString에서 가린다. */
public record IdentityVerifyCommand(int birthYear, Gender gender, String demoIdentityKey) {

    @Override
    public String toString() {
        return "IdentityVerifyCommand[gender=" + gender + ", birthYear=****, demoIdentityKey=****]";
    }
}
