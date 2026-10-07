package com.pitchmap.basecamp.domain;

/** 합류 신청의 상태다. EXPIRED는 결정되지 못한 채 베이스캠프가 확정·취소·완료되어 닫힌 신청이다. */
public enum BasecampApplicationStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELED,
    EXPIRED
}
