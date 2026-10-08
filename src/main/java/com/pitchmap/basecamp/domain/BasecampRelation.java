package com.pitchmap.basecamp.domain;

/** 요청한 회원이 어떤 베이스캠프와 맺은 관계다. 비로그인 요청자와 관계가 없는 회원은 NONE이다. */
public enum BasecampRelation {
    NONE,
    APPLICANT,
    MEMBER,
    LEADER
}
