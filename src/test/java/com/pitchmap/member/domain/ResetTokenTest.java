package com.pitchmap.member.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ResetTokenTest {

    private final SecureRandom random = new SecureRandom();

    @Test
    @DisplayName("[F-02][PW-02] 생성한 토큰은 base64url 문자만 쓰는 43자다")
    void generatedValueHas43UrlSafeCharacters() {
        // when
        String value = ResetToken.generate(random).value();

        // then
        assertThat(value).hasSize(43).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    @DisplayName("[F-02][PW-02] 두 번 생성하면 서로 다른 값이다")
    void twoGeneratedValuesDiffer() {
        // when
        String first = ResetToken.generate(random).value();
        String second = ResetToken.generate(random).value();

        // then
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("[F-02][PW-02] 생성한 값은 형식이 맞다고 판정한다")
    void acceptsGeneratedValue() {
        // when
        String value = ResetToken.generate(random).value();

        // then
        assertThat(ResetToken.isWellFormed(value)).isTrue();
    }

    @Test
    @DisplayName("[F-02][PW-02] null은 형식이 맞지 않다")
    void rejectsNull() {
        assertThat(ResetToken.isWellFormed(null)).isFalse();
    }

    @Test
    @DisplayName("[F-02][PW-02] 빈 문자열과 42자·44자는 형식이 맞지 않다")
    void rejectsWrongLength() {
        assertThat(ResetToken.isWellFormed("")).isFalse();
        assertThat(ResetToken.isWellFormed("a".repeat(42))).isFalse();
        assertThat(ResetToken.isWellFormed("a".repeat(43))).isTrue();
        assertThat(ResetToken.isWellFormed("a".repeat(44))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"+", "/", "=", " ", "\n", "가", "é"})
    @DisplayName("[F-02][PW-02] 허용 문자 밖의 문자가 하나라도 있으면 형식이 맞지 않다")
    void rejectsCharacterOutsideAlphabet(String illegal) {
        // given
        String value = "a".repeat(42) + illegal;

        // then
        assertThat(ResetToken.isWellFormed(value)).isFalse();
        assertThat(ResetToken.isWellFormed(illegal + "a".repeat(42))).isFalse();
    }

    @Test
    @DisplayName("[F-02][PW-02] 해시는 소문자 16진수 64자다")
    void hashIsLowercaseHex() {
        // when
        String hash = ResetToken.hash(ResetToken.generate(random).value());

        // then
        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("[F-02][PW-02] 같은 입력의 해시는 같고 다른 입력의 해시는 다르다")
    void hashIsDeterministicAndInputSensitive() {
        // given
        String first = ResetToken.generate(random).value();
        String second = ResetToken.generate(random).value();

        // then
        assertThat(ResetToken.hash(first)).isEqualTo(ResetToken.hash(first));
        assertThat(ResetToken.hash(first)).isNotEqualTo(ResetToken.hash(second));
    }

    @Test
    @DisplayName("[F-02][PW-02] 해시는 알려진 SHA-256 값과 같다")
    void hashMatchesKnownSha256() {
        assertThat(ResetToken.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    @DisplayName("[F-02][PW-02] toString은 토큰 값을 드러내지 않는다")
    void toStringHidesValue() {
        // given
        ResetToken token = ResetToken.generate(random);

        // then
        assertThat(token.toString()).doesNotContain(token.value());
    }
}
