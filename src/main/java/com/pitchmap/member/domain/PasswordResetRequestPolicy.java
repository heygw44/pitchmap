package com.pitchmap.member.domain;

import java.time.Duration;

/**
 * 비밀번호 재설정 요청 한도. 이메일 한도는 같은 주소로 보내는 메일 폭탄을, IP 한도는 한 곳에서 여러 주소를 찔러 보는 것을 막는다.
 * 키는 입력한 이메일 문자열과 IP이고 가입 여부를 보지 않는다. 가입 여부에 따라 응답이 달라지면 가입 여부가 드러난다.
 */
public final class PasswordResetRequestPolicy {

    /** 같은 이메일은 이전 요청 60초 뒤부터 다시 요청할 수 있고, 첫 요청부터 24시간에 5번까지 요청할 수 있다. */
    public static final Rule EMAIL = new Rule(Duration.ofSeconds(60), Duration.ofHours(24), 5);

    /** 같은 IP는 요청 사이 간격 제한 없이 첫 요청부터 1시간에 20번까지 요청할 수 있다. */
    public static final Rule IP = new Rule(Duration.ZERO, Duration.ofHours(1), 20);

    /** 마지막 요청 뒤 이만큼 지나면 그 행은 어느 규칙의 구간에도 걸리지 않아 쓸모가 없다. */
    public static final Duration STALE_AFTER = Duration.ofHours(24);

    private PasswordResetRequestPolicy() {}

    /** 호출하면 정규화한 이메일의 한도 키를 돌려준다. 원문 대신 해시를 저장하고, 접두사로 IP 키와 구분한다. */
    public static String emailKey(String normalizedEmail) {
        return ResetToken.hash("email:" + normalizedEmail);
    }

    /** 호출하면 IP의 한도 키를 돌려준다. 원문 대신 해시를 저장하고, 접두사로 이메일 키와 구분한다. */
    public static String ipKey(String ip) {
        return ResetToken.hash("ip:" + ip);
    }

    /**
     * 키 하나에 적용하는 한도.
     *
     * @param minInterval 이전 요청과의 최소 간격. 0이면 간격을 제한하지 않는다.
     * @param window 첫 요청부터 시작하는 고정 구간의 길이
     * @param maxRequests 구간 안에서 받는 요청 수
     */
    public record Rule(Duration minInterval, Duration window, int maxRequests) {}
}
