package com.pitchmap.trust.application;

/**
 * 신고를 조치한 결과. 제재를 내리지 않았으면 sanctionId가 null이다.
 * suspended는 내린 제재가 이용을 막는 정지이면 true다. 호출하는 서비스가 커밋 뒤에 세션을 지울지 정하는 데 쓴다.
 */
public record MemberReportActionResult(
        long reportId, String status, Long sanctionId, long targetMemberId, boolean suspended) {}
