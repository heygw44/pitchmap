package com.pitchmap.basecamp.application;

/**
 * 캠프 리더가 합류 신청을 승인하거나 거절한 이벤트의 종류와 내용. 승인·거절 서비스가 기록하고 신청자에게 알리는 처리기가 읽으므로
 * 이름과 형태를 한 곳에 둔다. 이메일, 신청 메시지 같은 개인정보는 싣지 않는다. 처리기가 필요하면 ID로 다시 읽는다.
 */
public final class BasecampDecisionEvents {

    public static final String APPROVED_EVENT_TYPE = "BASECAMP_APPROVED";
    public static final String REJECTED_EVENT_TYPE = "BASECAMP_REJECTED";
    public static final String AGGREGATE_TYPE = "BASECAMP";

    private BasecampDecisionEvents() {}

    /**
     * @param applicationId 승인하거나 거절한 합류 신청 ID
     * @param applicantId 알림을 받을 신청자 ID
     * @param basecampId 신청한 베이스캠프 ID
     */
    public record Payload(long applicationId, long applicantId, long basecampId) {}
}
