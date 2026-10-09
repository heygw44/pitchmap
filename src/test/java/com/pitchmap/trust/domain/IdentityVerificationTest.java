package com.pitchmap.trust.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class IdentityVerificationTest {

    private static final Instant NOW = Instant.parse("2026-10-05T03:00:00Z");
    private static final Year THIS_YEAR = Year.of(2026);
    private static final CiHash CI_HASH = new CiHash("a".repeat(64));

    @Test
    @DisplayName("[ID-03] 올해에서 19를 뺀 해에 태어난 사람은 성인이다")
    void bornNineteenYearsAgoIsAdult() {
        assertThat(verificationBornIn(2007).isAdult(THIS_YEAR)).isTrue();
    }

    @Test
    @DisplayName("[ID-03] 올해에서 18을 뺀 해에 태어난 사람은 성인이 아니다")
    void bornEighteenYearsAgoIsNotAdult() {
        assertThat(verificationBornIn(2008).isAdult(THIS_YEAR)).isFalse();
    }

    @Test
    @DisplayName("[ID-03] 같은 출생연도가 다음 해 1월 1일부터 성인으로 바뀐다")
    void becomesAdultOnNextJanuaryFirst() {
        IdentityVerification verification = verificationBornIn(2008);

        assertThat(verification.isAdult(Year.of(2026))).isFalse();
        assertThat(verification.isAdult(Year.of(2027))).isTrue();
    }

    @Test
    @DisplayName("[ID-03] 출생연도가 없으면 성인이 아니다")
    void withoutBirthYearIsNotAdult() {
        IdentityVerification verification = verificationBornIn(1990);
        ReflectionTestUtils.setField(verification, "birthYear", null);

        assertThat(verification.isAdult(THIS_YEAR)).isFalse();
    }

    @Test
    @DisplayName("[NFR-11] 연령대는 올해에서 출생연도를 뺀 나이로 계산하고, 미성년이나 출생연도가 없으면 비어 있다")
    void ageGroupFollowsAge() {
        assertThat(verificationBornIn(2007).ageGroupIn(THIS_YEAR)).contains(AgeGroup.TWENTIES);
        assertThat(verificationBornIn(1996).ageGroupIn(THIS_YEAR)).contains(AgeGroup.THIRTIES);
        assertThat(verificationBornIn(2008).ageGroupIn(THIS_YEAR)).isEmpty();
        IdentityVerification withdrawn = verificationBornIn(1990);
        ReflectionTestUtils.setField(withdrawn, "birthYear", null);
        assertThat(withdrawn.ageGroupIn(THIS_YEAR)).isEmpty();
    }

    @Test
    @DisplayName("[ID-01] 본인확인 기록에는 출생연도·성별·CI 해시·제공자·시각만 담긴다")
    void keepsOnlyMinimalFields() {
        IdentityVerification verification = verificationBornIn(1995);

        assertThat(verification.getBirthYear()).isEqualTo((short) 1995);
        assertThat(verification.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(verification.getCiHash()).isEqualTo(CI_HASH);
        assertThat(verification.getProvider()).isEqualTo(IdentityProviderType.FAKE);
        assertThat(verification.getVerifiedAt()).isEqualTo(NOW);
        assertThat(verification.getCiRetainedUntil()).isNull();
    }

    @Test
    @DisplayName("성별이 없거나 인자가 null이면 본인확인 기록을 만들 수 없다")
    void rejectsMissingValues() {
        assertThatThrownBy(() -> IdentityVerification.verify(
                        1L, new VerifiedIdentity("ci", 1995, null), CI_HASH, IdentityProviderType.FAKE, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IdentityVerification.verify(
                        1L, new VerifiedIdentity("ci", 1995, Gender.MALE), null, IdentityProviderType.FAKE, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[ID-02] CI 해시는 소문자 16진수 64자만 받고, toString에 값을 드러내지 않는다")
    void ciHashValidatesAndMasks() {
        assertThatThrownBy(() -> new CiHash("ABC")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CiHash("A".repeat(64))).isInstanceOf(IllegalArgumentException.class);
        assertThat(CI_HASH.toString()).doesNotContain("aaaa");
    }

    @Test
    @DisplayName("[PV-04][PV-05] 탈퇴하면 출생연도와 성별을 지우고 CI 해시는 남기며 보관 기한과 수정 시각을 적는다")
    void withdrawErasesBirthYearAndGenderAndKeepsCiHash() {
        IdentityVerification verification = verificationBornIn(1995);
        Instant withdrawnAt = NOW.plus(Duration.ofDays(3));
        Instant retainedUntil = IdentityVerification.ciRetainedUntil(withdrawnAt, false);

        verification.withdraw(retainedUntil, withdrawnAt);

        assertThat(verification.getBirthYear()).isNull();
        assertThat(verification.getGender()).isNull();
        assertThat(verification.getCiHash()).isEqualTo(CI_HASH);
        assertThat(verification.getCiRetainedUntil()).isEqualTo(retainedUntil);
        assertThat(verification.getUpdatedAt()).isEqualTo(withdrawnAt);
        assertThat(verification.isAdult(THIS_YEAR)).isFalse();
    }

    @Test
    @DisplayName("[PV-05] CI 해시 보관 기한은 제재 이력이 없으면 30일, 있으면 365일 뒤다")
    void ciRetentionDependsOnSanctionHistory() {
        assertThat(IdentityVerification.ciRetainedUntil(NOW, false)).isEqualTo(NOW.plus(Duration.ofDays(30)));
        assertThat(IdentityVerification.ciRetainedUntil(NOW, true)).isEqualTo(NOW.plus(Duration.ofDays(365)));
    }

    @Test
    @DisplayName("[PV-05] 보관 기한이나 수정 시각이 null이면 탈퇴 처리할 수 없다")
    void withdrawRejectsNull() {
        IdentityVerification verification = verificationBornIn(1995);

        assertThatThrownBy(() -> verification.withdraw(null, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> verification.withdraw(NOW, null)).isInstanceOf(IllegalArgumentException.class);
    }

    private static IdentityVerification verificationBornIn(int birthYear) {
        return IdentityVerification.verify(
                1L, new VerifiedIdentity("ci", birthYear, Gender.FEMALE), CI_HASH, IdentityProviderType.FAKE, NOW);
    }
}
