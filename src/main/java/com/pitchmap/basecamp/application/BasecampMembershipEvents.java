package com.pitchmap.basecamp.application;

/**
 * 베이스캠프 멤버가 빠진 이벤트의 종류와 내용. 강퇴 서비스가 기록하고 강퇴된 회원에게 알리는 처리기가 읽으므로 이름과 형태를 한 곳에 둔다.
 * 이메일, 연락 수단 같은 개인정보는 싣지 않는다. 처리기가 필요하면 ID로 다시 읽는다.
 */
public final class BasecampMembershipEvents {

    public static final String KICKED_EVENT_TYPE = "BASECAMP_KICKED";
    public static final String AGGREGATE_TYPE = "BASECAMP";

    private BasecampMembershipEvents() {}

    /**
     * @param basecampId 강퇴된 베이스캠프 ID
     * @param memberId 알림을 받을 강퇴된 회원 ID
     * @param reason 강퇴 사유 이름. 정해진 값 중 하나다
     */
    public record KickedPayload(long basecampId, long memberId, String reason) {}
}
