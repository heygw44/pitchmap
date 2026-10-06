package com.pitchmap.trust.application;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.trust.domain.CiHash;
import com.pitchmap.trust.domain.CiHasher;
import com.pitchmap.trust.domain.IdentityClaim;
import com.pitchmap.trust.domain.IdentityProvider;
import com.pitchmap.trust.domain.IdentityVerification;
import com.pitchmap.trust.domain.IdentityVerificationRepository;
import com.pitchmap.trust.domain.TrustErrorCode;
import com.pitchmap.trust.domain.VerifiedIdentity;
import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원이 본인확인을 한다. 서비스는 제공자가 돌려준 출생연도·성별과 CI 해시만 저장하고, CI 원값은 저장하지 않는다.
 *
 * <p>한 회원은 한 번만 본인확인할 수 있고, 같은 CI는 한 계정에만 연결된다. 서비스는 조회로 먼저 거르고, 두 요청이 동시에 들어와 조회를 모두 통과하면
 * 저장할 때 DB가 던지는 유니크 제약 위반을 같은 오류 코드로 바꾼다.
 */
@Service
@RequiredArgsConstructor
public class IdentityVerificationService {

    /** 이 해보다 이른 출생연도는 입력 실수로 보고 거부한다. */
    static final int MIN_BIRTH_YEAR = 1900;

    static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final IdentityVerificationRepository identityVerificationRepository;
    private final IdentityProvider identityProvider;
    private final CiHasher ciHasher;
    private final TrustSummaryService trustSummaryService;
    private final Clock clock;

    /**
     * 호출하면 memberId인 회원의 본인확인 기록을 저장하고 결과를 돌려준다.
     *
     * <p>출생연도가 1900년보다 이르거나 올해(한국 시각)보다 늦으면 INVALID_INPUT으로 거부한다. 이미 본인확인한 회원이면 IDENTITY_ALREADY_VERIFIED로,
     * 같은 CI로 본인확인한 다른 계정이 있으면 IDENTITY_CI_DUPLICATED로 거부한다. 두 경우가 겹치면 IDENTITY_ALREADY_VERIFIED가 먼저다.
     */
    @Transactional
    public IdentityVerificationResult verify(long memberId, IdentityVerifyCommand command) {
        Instant now = clock.instant();
        Year currentYear = Year.from(now.atZone(KOREA));
        requireBirthYearInRange(command.birthYear(), currentYear);
        if (identityVerificationRepository.existsByMemberId(memberId)) {
            throw new BusinessException(TrustErrorCode.IDENTITY_ALREADY_VERIFIED);
        }
        VerifiedIdentity identity = identityProvider.verify(
                new IdentityClaim(command.birthYear(), command.gender(), command.demoIdentityKey()));
        CiHash ciHash = ciHasher.hash(identity.ci());
        if (identityVerificationRepository.existsByCiHash(ciHash)) {
            throw new BusinessException(TrustErrorCode.IDENTITY_CI_DUPLICATED);
        }
        IdentityVerification saved =
                save(IdentityVerification.verify(memberId, identity, ciHash, identityProvider.type(), now));
        TrustSummary summary = trustSummaryService.summarizeOf(saved, currentYear);
        return new IdentityVerificationResult(
                summary.identityVerified(), saved.isAdult(currentYear), summary.trustLevel());
    }

    private IdentityVerification save(IdentityVerification verification) {
        try {
            return identityVerificationRepository.saveAndFlush(verification);
        } catch (DataIntegrityViolationException e) {
            throw IdentityUniqueConstraintTranslator.translate(e);
        }
    }

    private static void requireBirthYearInRange(int birthYear, Year currentYear) {
        if (birthYear < MIN_BIRTH_YEAR || birthYear > currentYear.getValue()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "출생연도는 " + MIN_BIRTH_YEAR + "년부터 올해까지여야 합니다.");
        }
    }
}
