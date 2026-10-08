package com.pitchmap.trust.application;

import java.time.Instant;

/**
 * 접수한 신고의 결과. 신고 때문에 대상 회원을 임시 정지했으면 temporarySuspensionEndsAt에 정지가 끝나는 시각이 있고, 아니면 null이다.
 */
public record MemberReportResult(long reportId, long targetMemberId, Instant temporarySuspensionEndsAt) {

    boolean isTargetSuspended() {
        return temporarySuspensionEndsAt != null;
    }
}
