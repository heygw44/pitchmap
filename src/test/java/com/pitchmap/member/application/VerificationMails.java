package com.pitchmap.member.application;

import com.pitchmap.common.mail.MailMessage;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/** 테스트가 인증 메일 본문에서 6자리 코드를 꺼낼 때 쓴다. 코드는 DB에 해시로만 있어서 메일 말고는 원값을 알 수 없다. */
public final class VerificationMails {

    private static final Pattern CODE = Pattern.compile("(?<!\\d)\\d{6}(?!\\d)");

    private VerificationMails() {}

    /** 본문에 6자리 숫자가 정확히 하나여야 한다. 코드가 본문에 한 번만 나오는지도 함께 확인하는 셈이다. */
    public static String extractCode(MailMessage mail) {
        List<String> codes =
                CODE.matcher(mail.body()).results().map(MatchResult::group).toList();
        if (codes.size() != 1) {
            throw new AssertionError("메일 본문에 6자리 코드가 정확히 하나여야 합니다. 찾은 개수=" + codes.size());
        }
        return codes.get(0);
    }
}
