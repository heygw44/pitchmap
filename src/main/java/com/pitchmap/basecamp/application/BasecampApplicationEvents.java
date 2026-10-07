package com.pitchmap.basecamp.application;

/**
 * 합류 신청 이벤트의 종류와 내용. 신청 서비스가 기록하고 캠프 리더에게 알리는 처리기가 읽으므로 이름과 형태를 한 곳에 둔다.
 * 이메일, 신청 메시지 같은 개인정보는 싣지 않는다. 처리기가 필요하면 ID로 다시 읽는다.
 */
public final class BasecampApplicationEvents {

    public static final String EVENT_TYPE = "BASECAMP_APPLIED";
    public static final String AGGREGATE_TYPE = "BASECAMP";

    private BasecampApplicationEvents() {}

    /**
     * @param applicationId 합류 신청 ID
     * @param applicantId 신청한 회원 ID
     * @param leaderId 알림을 받을 캠프 리더 ID
     */
    public record Payload(long applicationId, long applicantId, long leaderId) {}
}
