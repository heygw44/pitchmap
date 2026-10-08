package com.pitchmap.trust.application;

/**
 * 제재를 확정했을 때 후속 처리를 맡기는 이벤트의 종류와 내용. 제재 확정 서비스가 기록하고 받는 모듈의 처리기가 읽는다.
 *
 * <p>이벤트는 받는 쪽마다 따로 기록한다. 발행기는 이벤트 종류 하나에 처리기 하나만 두기 때문이고, 한 쪽의 처리가 실패해도 다른 쪽이
 * 영향을 받지 않게 하려는 것이다. 베이스캠프 정리 이벤트만 여기서 기록한다. basecamp 모듈은 trust 모듈의 클래스를 가져다 쓰지 않으므로,
 * 처리기 쪽에 같은 문자열을 따로 둔다. 두 값은 항상 같아야 한다.
 * 회원 ID만 싣고 사유 같은 내용은 싣지 않는다. 처리기가 필요하면 ID로 다시 읽는다.
 */
public final class SanctionEvents {

    public static final String BASECAMP_CLEANUP_EVENT_TYPE = "SANCTION_BASECAMP_CLEANUP";
    public static final String AGGREGATE_TYPE = "MEMBER";

    private SanctionEvents() {}

    /**
     * @param memberId 제재를 받은 회원 ID
     * @param sanctionId 확정한 제재 ID
     */
    public record Payload(long memberId, long sanctionId) {}
}
