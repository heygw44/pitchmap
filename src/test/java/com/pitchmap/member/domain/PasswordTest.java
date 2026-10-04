package com.pitchmap.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordTest {

    private static String ofLength(int length) {
        return "a1!" + "b".repeat(length - 3);
    }

    private static void assertPolicyViolation(String raw) {
        assertThatThrownBy(() -> Password.of(raw))
                .isInstanceOfSatisfying(
                        MemberException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_PASSWORD_POLICY));
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 64})
    @DisplayName("[F-01][PW-01] 길이가 10~64자이면 허용한다")
    void accepts_lengthWithinRange(int length) {
        assertThatCode(() -> Password.of(ofLength(length))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {9, 65})
    @DisplayName("[F-01][PW-01] 길이가 범위를 벗어나면 거부한다")
    void rejects_lengthOutOfRange(int length) {
        assertPolicyViolation(ofLength(length));
    }

    @Test
    @DisplayName("[F-01][PW-01] 영문이 없으면 거부한다")
    void rejects_withoutLetter() {
        assertPolicyViolation("1234567890!");
    }

    @Test
    @DisplayName("[F-01][PW-01] 숫자가 없으면 거부한다")
    void rejects_withoutDigit() {
        assertPolicyViolation("abcdefghij!");
    }

    @Test
    @DisplayName("[F-01][PW-01] 특수문자가 없으면 거부한다")
    void rejects_withoutSpecial() {
        assertPolicyViolation("abcdefghij1");
    }

    @Test
    @DisplayName("[F-01][PW-01] 공백이 들어 있으면 거부한다")
    void rejects_space() {
        assertPolicyViolation("abcde fghi1!");
    }

    @Test
    @DisplayName("[F-01][PW-01] 탭이 들어 있으면 거부한다")
    void rejects_tab() {
        assertPolicyViolation("abcde\tfghi1!");
    }

    @Test
    @DisplayName("[F-01][PW-01] 한글이 들어 있으면 거부한다")
    void rejects_korean() {
        assertPolicyViolation("abcdefghi1!가");
    }

    @Test
    @DisplayName("[F-01][PW-01] 전각 특수문자는 특수문자로 인정하지 않고 거부한다")
    void rejects_fullWidthSpecial() {
        assertPolicyViolation("abcdefghi1！");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "abcdefghi1!",
                "abcdefghi1@",
                "abcdefghi1#",
                "abcdefghi1$",
                "abcdefghi1%",
                "abcdefghi1^",
                "abcdefghi1&",
                "abcdefghi1*",
                "abcdefghi1~",
                "abcdefghi1\\"
            })
    @DisplayName("[F-01][PW-01] ASCII 특수문자는 허용한다")
    void accepts_asciiSpecials(String raw) {
        assertThatCode(() -> Password.of(raw)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullSource
    @DisplayName("[F-01][PW-01] null이면 거부한다")
    void rejects_null(String raw) {
        assertPolicyViolation(raw);
    }

    @Test
    @DisplayName("[F-01][PW-01] toString은 원문을 노출하지 않는다")
    void toString_hidesValue() {
        String raw = "secret-Pass1!";

        Password password = Password.of(raw);

        assertThat(password.toString()).doesNotContain(raw);
        assertThat(password.value()).isEqualTo(raw);
    }
}
