package com.pitchmap.basecamp.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.basecamp.domain.JoinEligibilityPolicy.Applicant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class JoinEligibilityPolicyTest {

    private static final JoinCondition NO_CONDITION = JoinCondition.none();

    @ParameterizedTest(name = "조건 {0}, 신청자 단계 {1} -> 충족 {2}")
    @CsvSource({"없음, 0, false", "없음, 1, true", "없음, 2, true", "1, 0, false", "1, 1, true", "2, 1, false", "2, 2, true"})
    @DisplayName("[F-12][BC-05] 신뢰 단계는 최소 단계(조건이 없으면 1)보다 낮으면 TRUST_LEVEL로 걸린다")
    void trustLevelBoundary(String minLevel, int trustLevel, boolean met) {
        // given
        JoinCondition condition =
                "없음".equals(minLevel) ? NO_CONDITION : NO_CONDITION.withMinTrustLevel(Integer.parseInt(minLevel));

        // when
        List<JoinUnmetReason> reasons = JoinEligibilityPolicy.unmetReasons(
                condition, applicant(trustLevel, 20, JoinGender.MALE), false, PriorRelation.NONE);

        // then
        assertThat(reasons.contains(JoinUnmetReason.TRUST_LEVEL)).isEqualTo(!met);
    }

    @ParameterizedTest(name = "범위 30~40, 신청자 연령대 {0} -> 충족 {1}")
    @CsvSource({"20, false", "30, true", "40, true", "50, false", "60, false"})
    @DisplayName("[F-12][BC-05] 연령대는 하한과 상한을 포함한 범위 안이어야 하고, 밖이면 AGE_GROUP으로 걸린다")
    void ageGroupBoundary(int ageGroup, boolean met) {
        // given
        JoinCondition condition = NO_CONDITION.withAgeGroupRange(30, 40);

        // when
        List<JoinUnmetReason> reasons = JoinEligibilityPolicy.unmetReasons(
                condition, applicant(1, ageGroup, JoinGender.MALE), false, PriorRelation.NONE);

        // then
        assertThat(reasons.contains(JoinUnmetReason.AGE_GROUP)).isEqualTo(!met);
    }

    @Test
    @DisplayName("[F-12][BC-05] 범위의 양 끝인 20과 60도 그 값의 연령대는 충족한다")
    void ageGroupExtremes() {
        // given
        JoinCondition condition = NO_CONDITION.withAgeGroupRange(20, 60);

        // when
        List<JoinUnmetReason> twenties = JoinEligibilityPolicy.unmetReasons(
                condition, applicant(1, 20, JoinGender.MALE), false, PriorRelation.NONE);
        List<JoinUnmetReason> sixtiesPlus = JoinEligibilityPolicy.unmetReasons(
                condition, applicant(1, 60, JoinGender.MALE), false, PriorRelation.NONE);

        // then
        assertThat(twenties).isEmpty();
        assertThat(sixtiesPlus).isEmpty();
    }

    @Test
    @DisplayName("[F-12][BC-05] 연령대 조건이 있는데 본인확인한 연령대가 없으면 AGE_GROUP으로 걸리고, 조건이 없으면 연령대가 없어도 통과한다")
    void ageGroupMissingVerification() {
        // given
        JoinCondition condition = NO_CONDITION.withAgeGroupRange(20, 30);

        // when
        List<JoinUnmetReason> withCondition = JoinEligibilityPolicy.unmetReasons(
                condition, applicant(1, null, JoinGender.MALE), false, PriorRelation.NONE);
        List<JoinUnmetReason> withoutCondition = JoinEligibilityPolicy.unmetReasons(
                NO_CONDITION, applicant(1, null, JoinGender.MALE), false, PriorRelation.NONE);

        // then
        assertThat(withCondition).containsExactly(JoinUnmetReason.AGE_GROUP);
        assertThat(withoutCondition).isEmpty();
    }

    @Test
    @DisplayName("[F-12][BC-05] 동성만 받는 조건이면 같은 성별은 통과하고 다른 성별이나 본인확인 성별이 없는 신청자는 GENDER로 걸린다")
    void sameGenderOnly() {
        // given
        JoinCondition condition = NO_CONDITION.withSameGenderOnly(JoinGender.FEMALE);

        // when
        List<JoinUnmetReason> same = JoinEligibilityPolicy.unmetReasons(
                condition, applicant(1, 20, JoinGender.FEMALE), false, PriorRelation.NONE);
        List<JoinUnmetReason> different = JoinEligibilityPolicy.unmetReasons(
                condition, applicant(1, 20, JoinGender.MALE), false, PriorRelation.NONE);
        List<JoinUnmetReason> unknown =
                JoinEligibilityPolicy.unmetReasons(condition, applicant(1, 20, null), false, PriorRelation.NONE);

        // then
        assertThat(same).isEmpty();
        assertThat(different).containsExactly(JoinUnmetReason.GENDER);
        assertThat(unknown).containsExactly(JoinUnmetReason.GENDER);
    }

    @Test
    @DisplayName("[F-12][BC-06] 같은 기간에 확정된 다른 베이스캠프가 있으면 DATE_CONFLICT로 걸린다")
    void dateConflict() {
        // when
        List<JoinUnmetReason> reasons = JoinEligibilityPolicy.unmetReasons(
                NO_CONDITION, applicant(1, 20, JoinGender.MALE), true, PriorRelation.NONE);

        // then
        assertThat(reasons).containsExactly(JoinUnmetReason.DATE_CONFLICT);
    }

    @Test
    @DisplayName("[F-12][BC-07] 이미 멤버이거나 대기 중이면 ALREADY_JOINED, 거절·탈퇴·강퇴된 적이 있으면 REAPPLY_NOT_ALLOWED로 걸린다")
    void priorRelation() {
        // given
        Applicant applicant = applicant(1, 20, JoinGender.MALE);

        // when
        List<JoinUnmetReason> none =
                JoinEligibilityPolicy.unmetReasons(NO_CONDITION, applicant, false, PriorRelation.NONE);
        List<JoinUnmetReason> active =
                JoinEligibilityPolicy.unmetReasons(NO_CONDITION, applicant, false, PriorRelation.ACTIVE);
        List<JoinUnmetReason> blocked =
                JoinEligibilityPolicy.unmetReasons(NO_CONDITION, applicant, false, PriorRelation.BLOCKED);

        // then
        assertThat(none).isEmpty();
        assertThat(active).containsExactly(JoinUnmetReason.ALREADY_JOINED);
        assertThat(blocked).containsExactly(JoinUnmetReason.REAPPLY_NOT_ALLOWED);
    }

    @Test
    @DisplayName("[F-12][BC-05] 이유가 여러 개이면 TRUST_LEVEL, AGE_GROUP, GENDER, DATE_CONFLICT, ALREADY_JOINED 순서로 모두 돌려준다")
    void multipleReasonsKeepDeclarationOrder() {
        // given
        JoinCondition condition =
                NO_CONDITION.withMinTrustLevel(2).withAgeGroupRange(30, 40).withSameGenderOnly(JoinGender.FEMALE);

        // when
        List<JoinUnmetReason> reasons =
                JoinEligibilityPolicy.unmetReasons(condition, applicant(0, null, null), true, PriorRelation.ACTIVE);

        // then
        assertThat(reasons)
                .containsExactly(
                        JoinUnmetReason.TRUST_LEVEL,
                        JoinUnmetReason.AGE_GROUP,
                        JoinUnmetReason.GENDER,
                        JoinUnmetReason.DATE_CONFLICT,
                        JoinUnmetReason.ALREADY_JOINED);
    }

    @Test
    @DisplayName("[F-12][BC-06] 두 기간은 양 끝을 포함해서 하루라도 겹치면 충돌이고, 하루도 겹치지 않으면 충돌이 아니다")
    void overlapsIncludesBothEnds() {
        // given
        LocalDate start = LocalDate.of(2026, 10, 20);
        LocalDate end = LocalDate.of(2026, 10, 22);

        // when & then
        assertThat(JoinEligibilityPolicy.overlaps(start, end, end, end.plusDays(2)))
                .isTrue();
        assertThat(JoinEligibilityPolicy.overlaps(start, end, start.minusDays(2), start))
                .isTrue();
        assertThat(JoinEligibilityPolicy.overlaps(start, end, start.plusDays(1), start.plusDays(1)))
                .isTrue();
        assertThat(JoinEligibilityPolicy.overlaps(start, end, end.plusDays(1), end.plusDays(3)))
                .isFalse();
        assertThat(JoinEligibilityPolicy.overlaps(start, end, start.minusDays(3), start.minusDays(1)))
                .isFalse();
    }

    private static Applicant applicant(int trustLevel, Integer ageGroup, JoinGender gender) {
        return new Applicant(trustLevel, ageGroup, gender);
    }
}
