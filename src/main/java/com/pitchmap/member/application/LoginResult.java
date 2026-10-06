package com.pitchmap.member.application;

import com.pitchmap.member.domain.MemberRole;
import com.pitchmap.member.domain.MemberStatus;

public record LoginResult(long memberId, String nickname, MemberStatus status, MemberRole role) {}
