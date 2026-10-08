package com.pitchmap.basecamp.application;

import java.util.List;

/**
 * 베이스캠프가 확정, 취소, 완료됐을 때의 이벤트 종류와 내용. 캠프 리더의 전이 서비스와 자동 처리 작업이 기록하고 멤버에게 알리는 처리기가 읽으므로 이름과 형태를 한 곳에 둔다.
 * 회원 ID만 싣고 이메일, 연락 수단 같은 개인정보는 싣지 않는다. 처리기가 필요하면 ID로 다시 읽는다.
 */
public final class BasecampTransitionEvents {

    public static final String CONFIRMED_EVENT_TYPE = "BASECAMP_CONFIRMED";
    public static final String CANCELED_EVENT_TYPE = "BASECAMP_CANCELED";
    public static final String COMPLETED_EVENT_TYPE = "BASECAMP_COMPLETED";
    public static final String AGGREGATE_TYPE = "BASECAMP";

    private BasecampTransitionEvents() {}

    /**
     * @param basecampId 전이한 베이스캠프 ID
     * @param memberIds 알림을 받을 회원 ID. 확정이면 캠프 리더를 포함한 ACTIVE 멤버 전원이고, 캠프 리더가 취소했으면 캠프 리더를 뺀 ACTIVE 멤버이고, 자동으로 취소하거나 완료했으면 캠프 리더를 포함한 ACTIVE 멤버 전원이다
     */
    public record Payload(long basecampId, List<Long> memberIds) {}
}
