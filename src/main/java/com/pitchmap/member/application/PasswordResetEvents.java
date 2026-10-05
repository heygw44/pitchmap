package com.pitchmap.member.application;

/**
 * 비밀번호 재설정 요청 이벤트의 종류와 내용. 요청 서비스가 기록하고 발송 처리기가 읽으므로 이름과 형태를 한 곳에 둔다.
 * 회원 ID는 이벤트의 aggregate ID가 담고, 토큰은 처리기가 메일을 보내기 직전에 만든다. 그래서 payload에는 싣는 값이 없다.
 */
public final class PasswordResetEvents {

    public static final String EVENT_TYPE = "PASSWORD_RESET_REQUESTED";
    public static final String AGGREGATE_TYPE = "MEMBER";

    private PasswordResetEvents() {}

    /** 싣는 값이 없는 payload. 처리기는 payload를 읽지 않는다. */
    public record Payload() {}
}
