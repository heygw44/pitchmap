package com.pitchmap.basecamp.domain;

/** 회원이 어떤 베이스캠프에 신청하기 전부터 맺은 관계 가운데, 신청 자격에 영향을 주는 것만 줄인 값이다. */
public enum PriorRelation {
    /** 신청하는 데 걸림돌이 없다. 관계가 없거나, 스스로 취소했거나, 신청이 만료된 경우다. */
    NONE,
    /** 이미 멤버(캠프 리더 포함)이거나 대기 중인 신청이 있다. */
    ACTIVE,
    /** 거절·탈퇴·강퇴된 적이 있어 다시 신청할 수 없다. */
    BLOCKED;

    /**
     * 멤버 행의 상태와 신청 행의 상태로 이전 관계를 판정한다. 두 값은 없으면 null이다.
     *
     * <p>멤버 행이 있으면 신청 행보다 우선한다. 승인된 신청의 멤버가 탈퇴했다면 신청 상태는 APPROVED로 남아 있어도 다시 신청할 수 없기 때문이다.
     */
    public static PriorRelation of(BasecampMemberStatus memberStatus, BasecampApplicationStatus applicationStatus) {
        if (memberStatus == BasecampMemberStatus.ACTIVE) {
            return ACTIVE;
        }
        if (memberStatus == BasecampMemberStatus.LEFT || memberStatus == BasecampMemberStatus.KICKED) {
            return BLOCKED;
        }
        if (applicationStatus == BasecampApplicationStatus.PENDING) {
            return ACTIVE;
        }
        if (applicationStatus == BasecampApplicationStatus.REJECTED) {
            return BLOCKED;
        }
        return NONE;
    }
}
