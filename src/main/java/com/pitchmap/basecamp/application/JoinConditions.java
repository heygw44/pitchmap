package com.pitchmap.basecamp.application;

import com.pitchmap.basecamp.domain.JoinCondition;
import com.pitchmap.basecamp.domain.JoinGender;
import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.trust.application.TrustDetail;

/** 합류 조건 요청을 도메인 값으로 바꾼다. 베이스캠프를 열 때와 고칠 때 같은 규칙을 쓰도록 한 곳에 둔다. */
final class JoinConditions {

    private JoinConditions() {}

    // 동성만 받는 조건의 성별은 요청이 아니라 캠프 리더가 본인확인한 성별로 정한다. 연령대의 한쪽만 보내면 거부한다.
    static JoinCondition from(BasecampOpenCommand.JoinConditionCommand requested, TrustDetail trust) {
        if (requested == null) {
            return JoinCondition.none();
        }
        try {
            JoinCondition condition = JoinCondition.none();
            if (requested.minTrustLevel() != null) {
                condition = condition.withMinTrustLevel(requested.minTrustLevel());
            }
            condition = withAgeGroupRange(condition, requested);
            if (requested.sameGenderOnly()) {
                condition = condition.withSameGenderOnly(JoinGender.valueOf(trust.verifiedGender()));
            }
            return condition;
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, e.getMessage());
        }
    }

    private static JoinCondition withAgeGroupRange(
            JoinCondition condition, BasecampOpenCommand.JoinConditionCommand requested) {
        Integer min = requested.ageGroupMin();
        Integer max = requested.ageGroupMax();
        if (min == null && max == null) {
            return condition;
        }
        if (min == null || max == null) {
            throw new IllegalArgumentException("연령대 하한과 상한은 함께 보내야 합니다.");
        }
        return condition.withAgeGroupRange(min, max);
    }
}
