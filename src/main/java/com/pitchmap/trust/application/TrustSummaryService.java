package com.pitchmap.trust.application;

import com.pitchmap.trust.domain.IdentityVerification;
import com.pitchmap.trust.domain.IdentityVerificationRepository;
import java.time.Clock;
import java.time.Year;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TrustSummaryService {

    private static final TrustSummary NOT_VERIFIED_LEVEL_ZERO = new TrustSummary(false, 0);

    private final IdentityVerificationRepository identityVerificationRepository;
    private final Clock clock;

    /** 호출하면 회원의 본인확인 여부와 신뢰 단계를 계산해 돌려준다. 본인확인 기록이 없으면 본인확인 전, 단계 0이다. */
    @Transactional(readOnly = true)
    public TrustSummary summarize(long memberId) {
        Year currentYear = Year.from(clock.instant().atZone(IdentityVerificationService.KOREA));
        return identityVerificationRepository
                .findByMemberId(memberId)
                .map(verification -> summarizeOf(verification, currentYear))
                .orElse(NOT_VERIFIED_LEVEL_ZERO);
    }

    // 본인확인을 마친 성인이 단계 1이다. 성인 여부는 본인확인 기록이 한 곳에서 판정한다.
    TrustSummary summarizeOf(IdentityVerification verification, Year currentYear) {
        return new TrustSummary(true, verification.isAdult(currentYear) ? 1 : 0);
    }
}
