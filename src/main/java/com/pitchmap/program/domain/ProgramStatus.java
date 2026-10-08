package com.pitchmap.program.domain;

/** 행사 자체의 상태. 신청을 받을 수 있는지는 이 값이 아니라 신청 시작·마감 시각으로 판단한다. */
public enum ProgramStatus {
    /** 열려 있거나 열릴 예정인 행사. */
    SCHEDULED,
    /** 관리자가 취소한 행사. */
    CANCELED
}
