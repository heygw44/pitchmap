package com.pitchmap.trust.domain;

/** 제재의 상태. 적용 중인 제재는 기간이 끝나면 만료되고, 관리자가 해제하거나 기각하면 해제된다. */
public enum SanctionStatus {
    ACTIVE,
    LIFTED,
    EXPIRED
}
