package com.pitchmap.notification.application;

import com.pitchmap.common.mail.MailMessage;
import com.pitchmap.notification.infra.NotificationMailRow;

/** 알림 한 건을 메일 한 통으로 바꾼다. 보내거나 읽지 않고 내용만 만드는 순수한 클래스다. */
final class NotificationMailFactory {

    private static final String SUBJECT_PREFIX = "[피치맵] ";
    private static final String LINK_LABEL = "자세히 보기: ";

    private NotificationMailFactory() {}

    /**
     * 호출하면 제목은 알림 제목 앞에 서비스 이름을 붙이고, 본문은 알림 본문 아래에 링크 줄을 붙인 메일을 만든다.
     * 알림에 연결할 화면이 없으면(link가 null) 링크 줄을 붙이지 않는다. baseUrl 끝의 슬래시는 지우고 붙인다.
     */
    static MailMessage create(NotificationMailRow row, String baseUrl) {
        String body = row.link() == null
                ? row.body()
                : row.body() + "\n\n" + LINK_LABEL + stripTrailingSlashes(baseUrl) + row.link();
        return new MailMessage(row.email(), SUBJECT_PREFIX + row.title(), body);
    }

    private static String stripTrailingSlashes(String url) {
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') {
            end--;
        }
        return url.substring(0, end);
    }
}
