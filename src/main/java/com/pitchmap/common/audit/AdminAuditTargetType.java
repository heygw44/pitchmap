package com.pitchmap.common.audit;

/** 감사 로그에서 조치 대상이 어느 테이블의 행인지 나타내는 종류. */
public final class AdminAuditTargetType {

    public static final String MEMBER_REPORT = "MEMBER_REPORT";
    public static final String SANCTION = "SANCTION";
    public static final String SPOT = "SPOT";
    public static final String PROGRAM = "PROGRAM";
    public static final String SYNC_JOB_RUN = "SYNC_JOB_RUN";

    private AdminAuditTargetType() {}
}
