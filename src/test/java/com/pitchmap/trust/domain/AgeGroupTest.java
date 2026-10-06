package com.pitchmap.trust.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AgeGroupTest {

    @Test
    @DisplayName("[NFR-11] 연 나이 18세는 성인이 아니라서 연령대가 없다")
    void minorHasNoAgeGroup() {
        assertThat(AgeGroup.fromAge(18)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "19, TWENTIES",
        "29, TWENTIES",
        "30, THIRTIES",
        "39, THIRTIES",
        "40, FORTIES",
        "49, FORTIES",
        "50, FIFTIES",
        "59, FIFTIES",
        "60, SIXTIES_PLUS",
        "100, SIXTIES_PLUS"
    })
    @DisplayName("[NFR-11] 연 나이 19~29는 20대이고 10년 단위로 올라가며 60세부터는 60대 이상이다")
    void groupsByAge(int age, AgeGroup expected) {
        assertThat(AgeGroup.fromAge(age)).contains(expected);
    }
}
