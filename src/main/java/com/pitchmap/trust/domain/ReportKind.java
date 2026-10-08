package com.pitchmap.trust.domain;

/** 신고 대상의 종류. 회원 자체를 신고하거나, 신고자가 받은 동행 후기를 신고한다. */
public enum ReportKind {
    MEMBER,
    REVIEW
}
