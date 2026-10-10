package com.pitchmap.program.domain;

/** 행사 신청의 상태. 결제 대기와 확정이 정원을 차지하는 활성 신청이다. */
public enum ProgramApplicationStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    CANCELED,
    EXPIRED
}
