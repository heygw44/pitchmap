package com.pitchmap.basecamp.domain;

/** 제재를 받은 회원을 베이스캠프에서 정리한 결과다. */
public enum MemberRemoval {
    /** 회원이 캠프 리더라서 베이스캠프를 취소했다. */
    LEADER_CANCELED,
    /** 회원이 멤버라서 탈퇴 처리했다. */
    MEMBER_LEFT,
    /** 회원이 대기 중인 신청자라서 신청을 취소했다. */
    APPLICATION_CANCELED,
    /** 회원이 이미 빠졌거나 베이스캠프가 이미 끝나서 아무것도 바꾸지 않았다. */
    NOTHING
}
