package com.pitchmap.community.application;

/** 관리자 조치의 감사 로그 detail로 저장할 값. 상태 전후와 검토를 마친 신고 수만 담고, 글·댓글·신고의 글자는 담지 않는다. */
public final class CommunityAuditDetails {

    private CommunityAuditDetails() {}

    public record Hide(String fromStatus, String toStatus) {}

    public record Restore(String fromStatus, String toStatus, int reviewedReportCount) {}
}
