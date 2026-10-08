package com.pitchmap.spot.application;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 관리자 조치의 감사 로그 detail로 저장할 값. 장소 종류와 상태 전후만 담고, 신고 내용은 담지 않는다. 값이 없는 필드는 JSON에서 뺀다. */
public final class SpotAuditDetails {

    private SpotAuditDetails() {}

    public record Hide(String spotType, String fromStatus, String toStatus) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Restore(String spotType, String fromStatus, String toStatus, int reviewedReportCount) {}
}
