package com.pitchmap.basecamp.domain;

/** 회원을 본인의 뜻과 상관없이 베이스캠프에서 정리하는 이유다. */
public enum RemovalCause {
    /** 회원이 이용 정지 제재를 받았다. */
    SANCTION,
    /** 회원이 탈퇴했다. */
    WITHDRAWAL
}
