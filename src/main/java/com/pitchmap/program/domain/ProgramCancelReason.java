package com.pitchmap.program.domain;

/** 신청이 취소되거나 만료된 이유. */
public enum ProgramCancelReason {
    /** 신청한 회원이 취소했다. */
    USER,
    /** 결제 기한이 지나 만료됐다. */
    EXPIRED,
    /** 회원이 제재를 받아 취소됐다. */
    SANCTIONED,
    /** 회원이 탈퇴해서 취소됐다. */
    WITHDRAWN,
    /** 관리자가 행사를 취소해서 함께 취소됐다. */
    PROGRAM_CANCELED
}
