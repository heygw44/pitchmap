package com.pitchmap.member.api;

import com.pitchmap.member.application.VerificationResult;
import com.pitchmap.member.domain.MemberStatus;

public record EmailVerificationResponse(MemberStatus status) {

    public static EmailVerificationResponse from(VerificationResult result) {
        return new EmailVerificationResponse(result.status());
    }
}
