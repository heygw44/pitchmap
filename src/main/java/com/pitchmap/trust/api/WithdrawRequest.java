package com.pitchmap.trust.api;

import jakarta.validation.constraints.NotBlank;

/** 회원 탈퇴 요청이다. 본인 확인을 위해 비밀번호를 다시 받는다. */
record WithdrawRequest(@NotBlank String password) {}
