package com.pitchmap.trust.application;

/**
 * 관리자가 신고를 조치하려는 요청.
 *
 * @param sanctionType 내릴 제재 종류의 이름(WARNING, SUSPEND_7D, SUSPEND_30D, PERMANENT). 제재를 내리지 않으면 null
 * @param sanctionReason 제재 사유. 제재를 내리지 않으면 null
 * @param hideReview 후기 신고에서 신고된 동행 후기를 숨길지 여부
 * @param note 처리 메모. 없으면 null
 */
public record MemberReportActionCommand(
        long reportId, long adminId, String sanctionType, String sanctionReason, boolean hideReview, String note) {}
