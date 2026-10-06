package com.pitchmap.spot.domain;

/** 박지를 신고하는 사유. */
public enum BakjiReportReason {
    /** 야영이 금지된 곳이다. */
    ILLEGAL_AREA,
    /** 더는 야영할 수 없는 곳이다. */
    CLOSED,
    /** 박지 정보가 사실과 다르다. */
    FALSE_INFO
}
