package com.pitchmap.basecamp.domain;

/** 모집을 마감한 이유다. 정원이 차서 자동으로 마감한 경우에만 빈자리가 생기면 모집을 다시 연다. */
public enum ClosedReason {
    AUTO_FULL,
    LEADER
}
