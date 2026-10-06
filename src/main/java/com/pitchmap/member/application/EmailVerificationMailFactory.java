package com.pitchmap.member.application;

import com.pitchmap.common.mail.MailMessage;

/** 이메일 인증 코드를 담은 메일을 만든다. 코드는 이 메일 본문에만 들어간다. */
final class EmailVerificationMailFactory {

    static final String SUBJECT = "[피치맵] 이메일 인증 코드";

    private static final String BODY_TEMPLATE = """
            안녕하세요, 피치맵입니다.

            아래 인증 코드를 10분 안에 입력해 주세요.

            인증 코드: %s

            직접 가입하지 않으셨다면 이 메일을 무시해 주세요. 별도로 하실 일은 없습니다.
            """;

    private EmailVerificationMailFactory() {}

    static MailMessage create(String email, String code) {
        return new MailMessage(email, SUBJECT, BODY_TEMPLATE.formatted(code));
    }
}
