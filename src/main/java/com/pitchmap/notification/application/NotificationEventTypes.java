package com.pitchmap.notification.application;

/**
 * 알림을 만드는 이벤트 종류 이름이다. 이벤트를 기록하는 basecamp·trust 모듈의 상수와 같은 값이어야 한다.
 * notification 모듈은 그 모듈의 클래스를 가져다 쓰지 않으므로 문자열을 따로 둔다. 기록하는 쪽의 값을 바꾸면 여기도 함께 바꾼다.
 */
final class NotificationEventTypes {

    static final String BASECAMP_APPLIED = "BASECAMP_APPLIED";
    static final String BASECAMP_APPROVED = "BASECAMP_APPROVED";
    static final String BASECAMP_REJECTED = "BASECAMP_REJECTED";
    static final String BASECAMP_KICKED = "BASECAMP_KICKED";
    static final String BASECAMP_CONFIRMED = "BASECAMP_CONFIRMED";
    static final String BASECAMP_CANCELED = "BASECAMP_CANCELED";
    static final String BASECAMP_COMPLETED = "BASECAMP_COMPLETED";
    static final String BASECAMP_MEMBER_CHANGED = "BASECAMP_MEMBER_CHANGED";
    static final String MEMBER_REPORT_RESOLVED = "MEMBER_REPORT_RESOLVED";
    static final String SANCTION_CONFIRMED = "SANCTION_CONFIRMED";

    private NotificationEventTypes() {}
}
