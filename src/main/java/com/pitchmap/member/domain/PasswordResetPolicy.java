package com.pitchmap.member.domain;

import java.time.Duration;

/** 비밀번호 재설정 링크의 유효 시간과 토큰 길이. 값은 이 클래스 한 곳에서만 정한다. */
public final class PasswordResetPolicy {

    public static final Duration TOKEN_VALIDITY = Duration.ofMinutes(30);

    /** 토큰 원값을 이루는 무작위 바이트 수. */
    public static final int TOKEN_BYTES = 32;

    private PasswordResetPolicy() {}
}
