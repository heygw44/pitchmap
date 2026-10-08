package com.pitchmap.notification.infra;

/**
 * 메일을 보낼 알림 한 건과 받는 사람의 주소다. MyBatis가 이름으로 매핑하므로, 구성요소 이름은 열 이름을 camelCase로 바꾼 것과 같아야 한다.
 * 주소가 로그나 예외 메시지에 실수로 찍히지 않도록 {@link #toString()}은 값을 숨긴다.
 *
 * @param link 화면 안의 경로. 연결할 화면이 없으면 null이다
 */
public record NotificationMailRow(long notificationId, String email, String title, String body, String link) {

    @Override
    public String toString() {
        return "NotificationMailRow[REDACTED]";
    }
}
