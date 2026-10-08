package com.pitchmap.trust.domain;

/** 제재의 종류. 임시 정지는 성희롱·위협 신고가 들어왔을 때 서버가 관리자 확인 전에 먼저 내린다. */
public enum SanctionType {
    WARNING,
    SUSPEND_7D,
    SUSPEND_30D,
    PERMANENT,
    TEMPORARY_72H
}
