package com.pitchmap.member.application;

import com.pitchmap.common.mail.MailMessage;
import java.util.List;
import java.util.regex.Pattern;

/** 테스트가 재설정 메일 본문에서 토큰을 꺼낼 때 쓴다. 토큰은 DB에 해시로만 있어서 메일 말고는 원값을 알 수 없다. */
public final class PasswordResetMails {

    private static final Pattern TOKEN = Pattern.compile("#token=([A-Za-z0-9_-]{43})");

    private PasswordResetMails() {}

    /** 본문에 {@code #token=} 링크가 정확히 하나여야 한다. 토큰이 본문에 한 번만 나오는지도 함께 확인하는 셈이다. */
    public static String extractToken(MailMessage mail) {
        List<String> tokens = TOKEN.matcher(mail.body())
                .results()
                .map(result -> result.group(1))
                .toList();
        if (tokens.size() != 1) {
            throw new AssertionError("메일 본문에 재설정 토큰이 정확히 하나여야 합니다. 찾은 개수=" + tokens.size());
        }
        return tokens.get(0);
    }
}
