package com.pitchmap.trust.application;

/**
 * 회원이 탈퇴했을 때 후속 처리를 맡기는 이벤트의 종류와 내용. 탈퇴 서비스가 기록하고 basecamp·program 모듈의 처리기가 읽는다.
 *
 * <p>제재 확정 때와 같은 이유로 받는 쪽마다 따로 기록한다. 발행기는 이벤트 종류 하나에 처리기 하나만 두기 때문이고,
 * 한 쪽의 처리가 실패해도 다른 쪽이 영향을 받지 않게 하려는 것이다. 처리기는 제재 정리와 같은 코드를 쓰고 사유만 다르다.
 * basecamp·program 모듈은 trust 모듈의 클래스를 가져다 쓰지 않으므로, 처리기 쪽에 같은 문자열을 따로 둔다. 두 값은 항상 같아야 한다.
 */
public final class WithdrawalEvents {

    public static final String BASECAMP_CLEANUP_EVENT_TYPE = "WITHDRAWAL_BASECAMP_CLEANUP";
    public static final String PROGRAM_CLEANUP_EVENT_TYPE = "WITHDRAWAL_PROGRAM_CLEANUP";
    public static final String AGGREGATE_TYPE = "MEMBER";

    private WithdrawalEvents() {}

    /** @param memberId 탈퇴한 회원 ID */
    public record Payload(long memberId) {}
}
