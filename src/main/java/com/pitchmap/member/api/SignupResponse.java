package com.pitchmap.member.api;

import com.pitchmap.member.application.SignupResult;
import com.pitchmap.member.domain.MemberStatus;

public record SignupResponse(Long memberId, MemberStatus status) {

    public static SignupResponse from(SignupResult result) {
        return new SignupResponse(result.memberId(), result.status());
    }
}
