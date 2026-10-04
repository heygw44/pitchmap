package com.pitchmap.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class VerificationCodeTest {

    private static final long MEMBER_ID = 1L;
    private static final String CODE = "123456";
    // printf '1:123456' | shasum -a 256 으로 따로 계산한 값이다. 해시 방식이 바뀌면 저장된 해시와 어긋나므로 값을 고정해 둔다.
    private static final String CODE_HASH = "0a5538553f1a5746c15e0ad5df86655c5663ef805941dcdab1d17b3ecbfe7718";

    @ParameterizedTest
    @ValueSource(ints = {0, 7, 42, 999, 12_345, 999_999})
    @DisplayName("[F-01][EV-01] 코드는 항상 숫자 6자리이고 앞자리는 0으로 채운다")
    void generatedCodeIsAlwaysSixDigitsPaddedWithZeros(int randomValue) {
        // given
        SecureRandom random = fixedRandom(randomValue);

        // when
        VerificationCode code = VerificationCode.generate(random);

        // then
        assertThat(code.value()).hasSize(6).isEqualTo("%06d".formatted(randomValue));
    }

    @Test
    @DisplayName("[F-01][EV-01] 실제 난수로 만든 코드는 모두 숫자 6자리 형식이다")
    void codesFromSecureRandomAreWellFormed() {
        // given
        SecureRandom random = new SecureRandom();

        // when & then
        for (int i = 0; i < 1_000; i++) {
            assertThat(VerificationCode.isWellFormed(
                            VerificationCode.generate(random).value()))
                    .isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"000000", "123456", "999999"})
    @DisplayName("[F-01][EV-01] 숫자 6자리는 올바른 형식이다")
    void sixDigitsAreWellFormed(String code) {
        assertThat(VerificationCode.isWellFormed(code)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"12345", "1234567", "12345a", " 12345", "12 456", "-12345", "１２３４５６", "١٢٣٤٥٦"})
    @DisplayName("[F-01][EV-01] 자리 수가 다르거나 숫자가 아닌 문자가 있으면 올바른 형식이 아니다")
    void wrongLengthOrNonDigitsAreNotWellFormed(String code) {
        assertThat(VerificationCode.isWellFormed(code)).isFalse();
    }

    @Test
    @DisplayName("[F-01][EV-01] 형식이 틀린 값으로는 코드를 만들 수 없다")
    void cannotCreateMalformedCode() {
        assertThatThrownBy(() -> new VerificationCode("12345")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[F-01][EV-01] 해시는 회원 ID와 코드를 \":\"로 이은 문자열의 SHA-256 소문자 16진수 64자다")
    void hashIsLowercaseSha256HexOfMemberIdAndCode() {
        // when
        String hash = VerificationCode.hash(MEMBER_ID, CODE);

        // then
        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}").isEqualTo(CODE_HASH);
    }

    @Test
    @DisplayName("[F-01][EV-01] 같은 코드라도 회원이 다르면 해시가 다르다")
    void sameCodeHasDifferentHashForDifferentMembers() {
        assertThat(VerificationCode.hash(1L, CODE)).isNotEqualTo(VerificationCode.hash(2L, CODE));
        assertThat(VerificationCode.hash(11L, "1")).isNotEqualTo(VerificationCode.hash(1L, "11"));
    }

    @Test
    @DisplayName("[F-01][EV-02] 저장된 해시와 같은 회원·같은 코드일 때만 일치한다")
    void matchesOnlyForSameMemberAndSameCode() {
        assertThat(VerificationCode.matches(MEMBER_ID, CODE, CODE_HASH)).isTrue();
        assertThat(VerificationCode.matches(MEMBER_ID, "123457", CODE_HASH)).isFalse();
        assertThat(VerificationCode.matches(2L, CODE, CODE_HASH)).isFalse();
    }

    @Test
    @DisplayName("[F-01][EV-02] 코드나 저장된 해시가 null이거나 해시가 잘려 있으면 일치하지 않는다")
    void doesNotMatchNullOrTruncatedHash() {
        assertThat(VerificationCode.matches(MEMBER_ID, null, CODE_HASH)).isFalse();
        assertThat(VerificationCode.matches(MEMBER_ID, CODE, null)).isFalse();
        assertThat(VerificationCode.matches(MEMBER_ID, CODE, CODE_HASH.substring(0, 63)))
                .isFalse();
        assertThat(VerificationCode.matches(MEMBER_ID, CODE, CODE_HASH.toUpperCase(Locale.ROOT)))
                .isFalse();
    }

    @Test
    @DisplayName("[F-01] toString은 코드 값을 드러내지 않는다")
    void toStringHidesValue() {
        // when
        String text = new VerificationCode(CODE).toString();

        // then
        assertThat(text).doesNotContain(CODE);
    }

    // 구현이 random.nextInt(bound)를 부르므로 그 값만 고정한다. 한 번에 한 값만 쓰는 테스트라서 SecureRandom을 상속해 쓴다.
    private static SecureRandom fixedRandom(int value) {
        return new SecureRandom() {
            @Override
            public int nextInt(int bound) {
                assertThat(bound).isEqualTo(1_000_000);
                return value;
            }
        };
    }
}
