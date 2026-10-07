package com.pitchmap.basecamp.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JoinConditionTest {

    @Test
    @DisplayName("[BC-05] 조건을 걸지 않으면 모든 값이 비어 있고 동성만도 꺼져 있다")
    void noneHasNoCondition() {
        JoinCondition condition = JoinCondition.none();

        assertThat(condition.getMinTrustLevel()).isNull();
        assertThat(condition.getAgeGroupMin()).isNull();
        assertThat(condition.getAgeGroupMax()).isNull();
        assertThat(condition.isSameGenderOnly()).isFalse();
        assertThat(condition.getRequiredGender()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    @DisplayName("[BC-05] 최소 신뢰 단계는 1 또는 2를 걸 수 있다")
    void acceptsTrustLevel(int level) {
        assertThat(JoinCondition.none().withMinTrustLevel(level).getMinTrustLevel())
                .isEqualTo(level);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 3, 10})
    @DisplayName("[BC-05] 최소 신뢰 단계가 1~2가 아니면 거부한다")
    void rejectsTrustLevel(int level) {
        assertThatThrownBy(() -> JoinCondition.none().withMinTrustLevel(level))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[BC-05] 연령대 하한과 상한이 같거나 하한이 더 작으면 걸 수 있다")
    void acceptsAgeGroupRange() {
        JoinCondition same = JoinCondition.none().withAgeGroupRange(30, 30);
        JoinCondition range = JoinCondition.none().withAgeGroupRange(20, 60);

        assertThat(same.getAgeGroupMin()).isEqualTo(30);
        assertThat(same.getAgeGroupMax()).isEqualTo(30);
        assertThat(range.getAgeGroupMin()).isEqualTo(20);
        assertThat(range.getAgeGroupMax()).isEqualTo(60);
    }

    @Test
    @DisplayName("[BC-05] 연령대 하한이 상한보다 크면 거부한다")
    void rejectsInvertedAgeRange() {
        assertThatThrownBy(() -> JoinCondition.none().withAgeGroupRange(40, 30))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10, 19, 25, 70})
    @DisplayName("[BC-05] 연령대가 20~60 사이의 십 단위가 아니면 거부한다")
    void rejectsInvalidAgeGroup(int ageGroup) {
        assertThatThrownBy(() -> JoinCondition.none().withAgeGroupRange(ageGroup, 60))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JoinCondition.none().withAgeGroupRange(20, ageGroup))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[BC-05] 동성만 받으면 캠프 리더의 성별이 함께 저장된다")
    void sameGenderOnlyKeepsGender() {
        JoinCondition condition = JoinCondition.none().withSameGenderOnly(JoinGender.FEMALE);

        assertThat(condition.isSameGenderOnly()).isTrue();
        assertThat(condition.getRequiredGender()).isEqualTo(JoinGender.FEMALE);
    }

    @Test
    @DisplayName("[BC-05] 동성만 받는데 성별이 없으면 거부한다")
    void rejectsSameGenderWithoutGender() {
        assertThatThrownBy(() -> JoinCondition.none().withSameGenderOnly(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[BC-05] 조건을 더해도 이미 건 다른 조건은 그대로 두고 원래 객체는 바뀌지 않는다")
    void withMethodsKeepOtherConditions() {
        JoinCondition base = JoinCondition.none().withMinTrustLevel(2);

        JoinCondition combined = base.withAgeGroupRange(20, 30).withSameGenderOnly(JoinGender.MALE);

        assertThat(combined.getMinTrustLevel()).isEqualTo(2);
        assertThat(combined.getAgeGroupMin()).isEqualTo(20);
        assertThat(base.getAgeGroupMin()).isNull();
        assertThat(base.isSameGenderOnly()).isFalse();
    }
}
