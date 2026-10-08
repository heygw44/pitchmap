package com.pitchmap.basecamp.application;

import java.util.List;

/**
 * 베이스캠프를 캠프 리더가 확정하거나 취소했을 때의 이벤트 종류와 내용. 전이 서비스가 기록하고 멤버에게 알리는 처리기가 읽으므로 이름과 형태를 한 곳에 둔다.
 * 회원 ID만 싣고 이메일, 연락 수단 같은 개인정보는 싣지 않는다. 처리기가 필요하면 ID로 다시 읽는다.
 */
public final class BasecampTransitionEvents {

    public static final String CONFIRMED_EVENT_TYPE = "BASECAMP_CONFIRMED";
    public static final String CANCELED_EVENT_TYPE = "BASECAMP_CANCELED";
    public static final String AGGREGATE_TYPE = "BASECAMP";

    private BasecampTransitionEvents() {}

    /**
     * @param basecampId 확정되거나 취소된 베이스캠프 ID
     * @param memberIds 알림을 받을 회원 ID. 확정이면 캠프 리더를 포함한 ACTIVE 멤버 전원이고, 취소이면 캠프 리더를 뺀 ACTIVE 멤버다
     */
    public record Payload(long basecampId, List<Long> memberIds) {}
}
