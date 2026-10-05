package com.pitchmap.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class SelfGenderTest {

    @ParameterizedTest
    @EnumSource(SelfGender.class)
    @DisplayName("허용 목록의 이름이면 같은 성별 값을 돌려준다")
    void parse_acceptsAllowedName(SelfGender expected) {
        assertThat(SelfGender.parse(expected.name())).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "female", "OTHER"})
    @DisplayName("허용 목록에 없는 성별 문자열이면 INVALID_INPUT이다")
    void parse_rejectsUnknownName(String name) {
        assertThatThrownBy(() -> SelfGender.parse(name))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
    }
}
