package com.pitchmap.trust.domain;

/**
 * 신고 유형. 유형마다 신고 종류가 정해져 있다. 성희롱·위협만 긴급 신고라서, 접수하는 즉시 대상 회원을 임시 정지한다.
 */
public enum ReportType {
    NO_SHOW(ReportKind.MEMBER, false),
    MONEY_REQUEST(ReportKind.MEMBER, false),
    HARASSMENT_OR_THREAT(ReportKind.MEMBER, true),
    OFFENSIVE_BEHAVIOR(ReportKind.MEMBER, false),
    FAKE_PROFILE(ReportKind.MEMBER, false),
    ILLEGAL_CAMPING_INDUCEMENT(ReportKind.MEMBER, false),
    INAPPROPRIATE_REVIEW(ReportKind.REVIEW, false);

    private final ReportKind kind;
    private final boolean urgent;

    ReportType(ReportKind kind, boolean urgent) {
        this.kind = kind;
        this.urgent = urgent;
    }

    public ReportKind kind() {
        return kind;
    }

    public boolean isUrgent() {
        return urgent;
    }
}
