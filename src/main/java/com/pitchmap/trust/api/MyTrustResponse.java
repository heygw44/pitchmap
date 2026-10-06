package com.pitchmap.trust.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pitchmap.trust.application.TrustDetail;
import com.pitchmap.trust.domain.CompanionRecord;

/**
 * 내 신뢰 단계 응답이다. 단계 2이면 더 올라갈 단계가 없으므로 {@code nextLevel} 필드를 아예 뺀다.
 * 단계 0·1이면 단계 2 조건과 현재 진행 상황을 담는다.
 */
public record MyTrustResponse(
        int trustLevel,
        boolean identityVerified,
        @JsonInclude(JsonInclude.Include.NON_NULL) NextLevel nextLevel) {

    private static final int MAX_LEVEL = 2;

    public static MyTrustResponse from(TrustDetail detail) {
        NextLevel nextLevel = null;
        if (detail.trustLevel() < MAX_LEVEL) {
            nextLevel = new NextLevel(
                    MAX_LEVEL,
                    new Progress(detail.completedCompanions(), CompanionRecord.REQUIRED_COMPLETED_COMPANIONS),
                    new RateProgress(detail.rejoinRate(), CompanionRecord.REQUIRED_REJOIN_PERCENT),
                    detail.noRecentSanction());
        }
        return new MyTrustResponse(detail.trustLevel(), detail.identityVerified(), nextLevel);
    }

    /** 다음 단계의 조건이다. */
    public record NextLevel(
            int level, Progress completedCompanions, RateProgress rejoinRate, boolean noRecentSanction) {}

    /** 횟수 조건의 현재 값과 필요한 값이다. */
    public record Progress(int current, int required) {}

    /** 비율 조건의 현재 값(정수 %)과 필요한 값이다. 받은 후기가 없어 비율을 정할 수 없으면 current는 null이다. */
    public record RateProgress(Integer current, int required) {}
}
