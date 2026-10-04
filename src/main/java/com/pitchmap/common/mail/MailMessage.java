package com.pitchmap.common.mail;

import java.util.Objects;

/**
 * 보낼 메일 한 통. 수신자 주소와 본문(인증 코드 같은 값이 들어간다)은 개인정보이고 비밀값이다.
 * 그래서 로그나 예외 메시지에 실수로 찍히지 않도록 {@link #toString()}은 값을 숨긴다.
 *
 * @param to 수신자 이메일 주소
 * @param subject 제목
 * @param body 본문(일반 텍스트)
 */
public record MailMessage(String to, String subject, String body) {

    public MailMessage {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(body, "body");
    }

    @Override
    public String toString() {
        return "MailMessage[REDACTED]";
    }
}
