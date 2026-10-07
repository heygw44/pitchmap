package com.pitchmap.basecamp.domain;

import static com.pitchmap.basecamp.domain.BasecampAssertions.assertFailsWith;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CapacityTest {

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 6})
    @DisplayName("[BC-02] 정원은 캠프 리더를 포함해 2~6명이면 만들어진다")
    void acceptsRange(int value) {
        assertThat(Capacity.of(value).getValue()).isEqualTo((short) value);
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 1, 7, 100})
    @DisplayName("[BC-02] 정원이 2명 미만이거나 6명을 넘으면 BASECAMP_CAPACITY_INVALID다")
    void rejectsOutOfRange(int value) {
        assertFailsWith(BasecampErrorCode.BASECAMP_CAPACITY_INVALID, () -> Capacity.of(value));
    }

    @Test
    @DisplayName("[BC-02] 정원 비교는 숫자 크기로 한다")
    void comparesBySize() {
        assertThat(Capacity.of(3).isLessThan(Capacity.of(4))).isTrue();
        assertThat(Capacity.of(4).isLessThan(Capacity.of(4))).isFalse();
        assertThat(Capacity.of(5).isLessThan(Capacity.of(4))).isFalse();
    }

    @Test
    @DisplayName("[BC-08] 인원이 정원에 닿으면 가득 찬 것으로 본다")
    void isFilledWhenHeadcountReachesCapacity() {
        assertThat(Capacity.of(3).isFilledBy(2)).isFalse();
        assertThat(Capacity.of(3).isFilledBy(3)).isTrue();
    }

    @Test
    @DisplayName("[BC-02] 값이 같은 정원은 같은 객체로 본다")
    void equalsByValue() {
        assertThat(Capacity.of(4)).isEqualTo(Capacity.of(4)).hasSameHashCodeAs(Capacity.of(4));
        assertThat(Capacity.of(4)).isNotEqualTo(Capacity.of(5));
    }
}
