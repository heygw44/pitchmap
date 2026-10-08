package com.pitchmap.trust.domain;

/** 신고의 처리 상태. 접수한 신고는 검토를 거쳐 조치하거나 기각한다. */
public enum ReportStatus {
    RECEIVED,
    IN_REVIEW,
    ACTIONED,
    DISMISSED
}
