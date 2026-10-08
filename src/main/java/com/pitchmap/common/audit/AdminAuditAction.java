package com.pitchmap.common.audit;

/** 감사 로그에 남기는 관리자 조치의 종류. 관리자 요청 하나는 이 중 하나로 한 행을 남긴다. */
public enum AdminAuditAction {
    REPORT_START_REVIEW,
    REPORT_ACTION,
    REPORT_DISMISS,
    SANCTION_LIFT,
    SYNC_JOB_RUN
}
