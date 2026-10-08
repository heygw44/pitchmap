package com.pitchmap.basecamp.application;

import java.util.List;

/**
 * 베이스캠프 멤버가 빠진 이벤트의 종류와 내용. 강퇴 서비스와 제재 후 정리가 기록하고 알리는 처리기가 읽으므로 이름과 형태를 한 곳에 둔다.
 * 이메일, 연락 수단 같은 개인정보는 싣지 않는다. 처리기가 필요하면 ID로 다시 읽는다.
 */
public final class BasecampMembershipEvents {

    public static final String KICKED_EVENT_TYPE = "BASECAMP_KICKED";
    public static final String MEMBER_CHANGED_EVENT_TYPE = "BASECAMP_MEMBER_CHANGED";
    public static final String AGGREGATE_TYPE = "BASECAMP";

    private BasecampMembershipEvents() {}

    /**
     * @param basecampId 강퇴된 베이스캠프 ID
     * @param memberId 알림을 받을 강퇴된 회원 ID
     * @param reason 강퇴 사유 이름. 정해진 값 중 하나다
     */
    public record KickedPayload(long basecampId, long memberId, String reason) {}

    /**
     * @param basecampId 멤버가 바뀐 베이스캠프 ID
     * @param memberIds 알림을 받을 회원 ID. 멤버가 빠진 뒤에 남은 ACTIVE 멤버 전원이고 캠프 리더를 포함한다
     */
    public record MemberChangedPayload(long basecampId, List<Long> memberIds) {}
}
