package com.pitchmap.basecamp.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 회원이 베이스캠프에 합류 신청을 할 수 있는지 가리는 규칙이다. 검색 결과의 신청 가능 표시와 신청 검사가 같은 규칙을 쓴다. */
public final class JoinEligibilityPolicy {

    /** 합류 조건을 걸지 않아도 신청하려면 이 신뢰 단계 이상이어야 한다. 본인확인을 마친 성인이 단계 1이다. */
    public static final int BASE_TRUST_LEVEL = 1;

    private JoinEligibilityPolicy() {}

    /**
     * 신청하는 회원의 자격 정보다.
     *
     * @param trustLevel 신뢰 단계
     * @param verifiedAgeGroup 본인확인한 연령대(20~60의 십 단위, 60은 60대 이상). 본인확인 전이거나 미성년이면 null
     * @param verifiedGender 본인확인한 성별. 본인확인 전이면 null
     */
    public record Applicant(int trustLevel, Integer verifiedAgeGroup, JoinGender verifiedGender) {}

    /**
     * 신청할 수 없는 이유를 {@link JoinUnmetReason} 선언 순서대로 모두 돌려준다. 비어 있으면 신청할 수 있다.
     *
     * <p>연령대와 성별은 신청자가 스스로 밝힌 값이 아니라 본인확인한 값으로만 판정한다. 그래서 값이 없는 회원은 조건이 걸려 있으면 충족하지 못한 것으로 본다.
     *
     * @param dateConflict 같은 기간에 확정된 다른 베이스캠프의 멤버이면 true
     */
    public static List<JoinUnmetReason> unmetReasons(
            JoinCondition condition, Applicant applicant, boolean dateConflict, PriorRelation priorRelation) {
        List<JoinUnmetReason> reasons = new ArrayList<>();
        if (!meetsTrustLevel(condition, applicant)) {
            reasons.add(JoinUnmetReason.TRUST_LEVEL);
        }
        if (!meetsAgeGroup(condition, applicant)) {
            reasons.add(JoinUnmetReason.AGE_GROUP);
        }
        if (!meetsGender(condition, applicant)) {
            reasons.add(JoinUnmetReason.GENDER);
        }
        if (dateConflict) {
            reasons.add(JoinUnmetReason.DATE_CONFLICT);
        }
        if (priorRelation == PriorRelation.ACTIVE) {
            reasons.add(JoinUnmetReason.ALREADY_JOINED);
        }
        if (priorRelation == PriorRelation.BLOCKED) {
            reasons.add(JoinUnmetReason.REAPPLY_NOT_ALLOWED);
        }
        return List.copyOf(reasons);
    }

    /** 두 기간이 하루라도 겹치면 true다. 출발일과 종료일을 모두 포함해서 비교하므로 한쪽의 종료일이 다른 쪽의 출발일이어도 겹친 것이다. */
    public static boolean overlaps(LocalDate startA, LocalDate endA, LocalDate startB, LocalDate endB) {
        return !startA.isAfter(endB) && !endA.isBefore(startB);
    }

    private static boolean meetsTrustLevel(JoinCondition condition, Applicant applicant) {
        int required =
                Math.max(BASE_TRUST_LEVEL, condition.getMinTrustLevel() == null ? 0 : condition.getMinTrustLevel());
        return applicant.trustLevel() >= required;
    }

    private static boolean meetsAgeGroup(JoinCondition condition, Applicant applicant) {
        Integer min = condition.getAgeGroupMin();
        Integer max = condition.getAgeGroupMax();
        if (min == null || max == null) {
            return true;
        }
        Integer ageGroup = applicant.verifiedAgeGroup();
        return ageGroup != null && ageGroup >= min && ageGroup <= max;
    }

    private static boolean meetsGender(JoinCondition condition, Applicant applicant) {
        if (!condition.isSameGenderOnly()) {
            return true;
        }
        return applicant.verifiedGender() != null && applicant.verifiedGender() == condition.getRequiredGender();
    }
}
