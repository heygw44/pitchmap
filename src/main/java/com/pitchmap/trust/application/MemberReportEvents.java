package com.pitchmap.trust.application;

/**
 * 관리자가 신고를 조치하거나 기각했을 때 신고자에게 처리 결과를 알리려고 기록하는 이벤트의 종류와 내용.
 *
 * <p>신고자에게는 처리 결과만 알리고 제재 내용은 알리지 않는다. 그래서 payload에는 신고 ID, 신고자 ID, 결과만 싣고
 * 제재 종류나 사유, 처리 메모는 싣지 않는다. 이 이벤트의 처리기는 알림 기능을 만들 때 추가하므로, 그 전까지는 기록만 쌓인다.
 */
public final class MemberReportEvents {

    public static final String RESOLVED_EVENT_TYPE = "MEMBER_REPORT_RESOLVED";
    public static final String AGGREGATE_TYPE = "MEMBER_REPORT";

    private MemberReportEvents() {}

    /**
     * @param reportId 처리한 신고 ID
     * @param reporterId 신고한 회원 ID
     * @param result 처리 결과. ACTIONED(조치) 또는 DISMISSED(기각)
     */
    public record ResolvedPayload(long reportId, long reporterId, String result) {}
}
