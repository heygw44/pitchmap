package com.pitchmap.trust.application;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 관리자 조치의 감사 로그 detail로 저장할 값. 식별 정보와 상태 전후만 담고, 신고 내용·메모·제재 사유 원문은 담지 않는다.
 * 값이 없는 필드는 JSON에서 뺀다.
 */
public final class TrustAuditDetails {

    private TrustAuditDetails() {}

    public record StartReview(String fromStatus, String toStatus) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Action(
            String fromStatus,
            String toStatus,
            boolean hideReview,
            Long sanctionId,
            String sanctionType,
            Integer level) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Dismiss(String fromStatus, String toStatus, Long liftedSanctionId) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Lift(long memberId, String sanctionType, Integer level, String fromStatus, String toStatus) {}
}
