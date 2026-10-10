package com.pitchmap.notification.application;

import java.util.List;

/**
 * 알림을 만드는 이벤트 종류 이름이다. 이벤트를 기록하는 basecamp·trust·program 모듈의 상수와 같은 값이어야 한다.
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
    static final String PROGRAM_APPLICATION_CONFIRMED = "PROGRAM_APPLICATION_CONFIRMED";
    static final String PROGRAM_APPLICATION_CANCELED = "PROGRAM_APPLICATION_CANCELED";
    static final String PROGRAM_APPLICATION_EXPIRED = "PROGRAM_APPLICATION_EXPIRED";
    static final String PROGRAM_SEAT_RELEASED = "PROGRAM_SEAT_RELEASED";

    /** 회원이 이메일 수신 여부를 정할 수 있는 알림 종류다. 설정 화면에 이 순서대로 보여 준다. */
    static final List<String> EMAIL_CONFIGURABLE = List.of(
            BASECAMP_APPLIED,
            BASECAMP_APPROVED,
            BASECAMP_REJECTED,
            BASECAMP_KICKED,
            BASECAMP_CONFIRMED,
            BASECAMP_CANCELED,
            BASECAMP_COMPLETED,
            BASECAMP_MEMBER_CHANGED,
            MEMBER_REPORT_RESOLVED,
            SANCTION_CONFIRMED,
            PROGRAM_APPLICATION_CONFIRMED,
            PROGRAM_APPLICATION_CANCELED,
            PROGRAM_APPLICATION_EXPIRED,
            PROGRAM_SEAT_RELEASED);

    /** 회원이 설정을 저장하지 않은 알림 종류의 이메일 수신 여부다. 기본은 받는 것이다. */
    static final boolean DEFAULT_EMAIL_ENABLED = true;

    private NotificationEventTypes() {}
}
