package com.pitchmap.member.application;

import com.pitchmap.member.domain.MemberStatus;

public record SignupResult(Long memberId, MemberStatus status) {}
