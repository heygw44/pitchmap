package com.pitchmap.trust.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.trust.domain.CiHash;
import com.pitchmap.trust.domain.CiHasher;
import com.pitchmap.trust.domain.Gender;
import com.pitchmap.trust.domain.IdentityProvider;
import com.pitchmap.trust.domain.IdentityProviderType;
import com.pitchmap.trust.domain.IdentityVerification;
import com.pitchmap.trust.domain.IdentityVerificationRepository;
import com.pitchmap.trust.domain.TrustErrorCode;
import com.pitchmap.trust.domain.VerifiedIdentity;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdentityVerificationServiceTest {

    private static final long MEMBER_ID = 7L;
    private static final CiHash CI_HASH = new CiHash("c".repeat(64));

    private final IdentityVerificationRepository repository = mock(IdentityVerificationRepository.class);
    private final IdentityProvider provider = mock(IdentityProvider.class);
    private final CiHasher hasher = mock(CiHasher.class);

    @Test
    @DisplayName("[ID-03] 한국 시각 2025-12-31 23:59:59에는 2007년생이 미성년이고, 2026-01-01 0시에는 성인이다")
    void adultJudgementFollowsKoreaYear() {
        // given
        stubSuccess(2007);

        // when
        IdentityVerificationResult beforeNewYear =
                serviceAt("2025-12-31T14:59:59Z").verify(MEMBER_ID, command(2007));
        IdentityVerificationResult afterNewYear =
                serviceAt("2025-12-31T15:00:00Z").verify(MEMBER_ID, command(2007));

        // then
        assertThat(beforeNewYear).isEqualTo(new IdentityVerificationResult(true, false, 0));
        assertThat(afterNewYear).isEqualTo(new IdentityVerificationResult(true, true, 1));
    }

    @Test
    @DisplayName("[ID-01] 출생연도가 1900년보다 이르거나 올해(한국 시각)보다 늦으면 INVALID_INPUT이고 저장하지 않는다")
    void rejectsOutOfRangeBirthYear() {
        IdentityVerificationService service = serviceAt("2026-10-05T03:00:00Z");

        assertThatThrownBy(() -> service.verify(MEMBER_ID, command(1899)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
        assertThatThrownBy(() -> service.verify(MEMBER_ID, command(2027)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("[ID-04] 이미 본인확인한 회원이면 CI가 중복이어도 IDENTITY_ALREADY_VERIFIED가 먼저다")
    void alreadyVerifiedBeatsCiDuplicated() {
        when(repository.existsByMemberId(MEMBER_ID)).thenReturn(true);
        when(repository.existsByCiHash(any())).thenReturn(true);

        assertThatThrownBy(() -> serviceAt("2026-10-05T03:00:00Z").verify(MEMBER_ID, command(1995)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(TrustErrorCode.IDENTITY_ALREADY_VERIFIED));
    }

    @Test
    @DisplayName("[ID-04] 같은 CI로 본인확인한 다른 계정이 있으면 IDENTITY_CI_DUPLICATED이고 저장하지 않는다")
    void duplicatedCiIsRejected() {
        stubSuccess(1995);
        when(repository.existsByCiHash(CI_HASH)).thenReturn(true);

        assertThatThrownBy(() -> serviceAt("2026-10-05T03:00:00Z").verify(MEMBER_ID, command(1995)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(TrustErrorCode.IDENTITY_CI_DUPLICATED));
        verify(repository, never()).saveAndFlush(any());
    }

    private void stubSuccess(int birthYear) {
        when(provider.verify(any())).thenReturn(new VerifiedIdentity("ci-raw", birthYear, Gender.FEMALE));
        when(provider.type()).thenReturn(IdentityProviderType.FAKE);
        when(hasher.hash("ci-raw")).thenReturn(CI_HASH);
        when(repository.saveAndFlush(any(IdentityVerification.class))).thenAnswer(call -> call.getArgument(0));
    }

    private IdentityVerificationService serviceAt(String instant) {
        MutableClock clock = MutableClock.at(Instant.parse(instant));
        return new IdentityVerificationService(
                repository, provider, hasher, new TrustSummaryService(repository, clock), clock);
    }

    private static IdentityVerifyCommand command(int birthYear) {
        return new IdentityVerifyCommand(birthYear, Gender.FEMALE, "demo-1");
    }
}
