package com.pitchmap.member.api;

import com.pitchmap.member.application.LoginResult;
import com.pitchmap.member.domain.MemberRole;
import com.pitchmap.member.domain.MemberStatus;

public record LoginResponse(Long memberId, String nickname, MemberStatus status, MemberRole role) {

    public static LoginResponse from(LoginResult result) {
        return new LoginResponse(result.memberId(), result.nickname(), result.status(), result.role());
    }
}
