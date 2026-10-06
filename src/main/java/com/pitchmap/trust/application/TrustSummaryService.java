package com.pitchmap.trust.application;

import com.pitchmap.trust.domain.CompanionRecord;
import com.pitchmap.trust.domain.IdentityVerification;
import com.pitchmap.trust.domain.IdentityVerificationRepository;
import com.pitchmap.trust.domain.TrustLevelPolicy;
import java.time.Clock;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TrustSummaryService {

    private static final TrustSummary NOT_VERIFIED_LEVEL_ZERO = new TrustSummary(false, 0);

    // 동행·후기·제재 기록을 남기는 기능이 아직 없다. 그래서 지금은 모든 회원이 완료한 동행 0회, 받은 후기 0건이고
    // 확정된 제재도 없는 것으로 계산한다. 기록이 생기면 회원별로 읽어 오도록 바꾼다.
    private static final CompanionRecord COMPANION_RECORD = CompanionRecord.NONE;

    private final IdentityVerificationRepository identityVerificationRepository;
    private final Clock clock;

    /** 호출하면 회원의 본인확인 여부와 신뢰 단계를 계산해 돌려준다. 본인확인 기록이 없으면 본인확인 전, 단계 0이다. */
    @Transactional(readOnly = true)
    public TrustSummary summarize(long memberId) {
        Year currentYear = currentYear();
        return identityVerificationRepository
                .findByMemberId(memberId)
                .map(verification -> summarizeOf(verification, currentYear))
                .orElse(NOT_VERIFIED_LEVEL_ZERO);
    }

    /**
     * 호출하면 신뢰 단계와 그 근거(완료 동행, "다시 동행" 비율, 제재 여부)와 본인확인한 연령대·성별을 돌려준다.
     * 연령대는 본인확인한 성인에게만 있고, 성별은 본인확인한 회원이면 미성년이어도 있다.
     */
    @Transactional(readOnly = true)
    public TrustDetail detail(long memberId) {
        Year currentYear = currentYear();
        Optional<IdentityVerification> found = identityVerificationRepository.findByMemberId(memberId);
        boolean adult =
                found.map(verification -> verification.isAdult(currentYear)).orElse(false);
        int trustLevel = TrustLevelPolicy.judge(found.isPresent(), adult, COMPANION_RECORD);
        String verifiedAgeGroup = found.flatMap(verification -> verification.ageGroupIn(currentYear))
                .map(Enum::name)
                .orElse(null);
        String verifiedGender =
                found.map(verification -> verification.getGender().name()).orElse(null);
        return new TrustDetail(
                found.isPresent(),
                adult,
                trustLevel,
                COMPANION_RECORD.completedCompanions(),
                COMPANION_RECORD.rejoinRate(),
                !COMPANION_RECORD.recentSanction(),
                List.of(),
                verifiedAgeGroup,
                verifiedGender);
    }

    // 본인확인을 마친 성인이 단계 1이고, 동행 기록이 단계 2 조건을 채우면 단계 2다. 성인 여부는 본인확인 기록이 한 곳에서 판정한다.
    TrustSummary summarizeOf(IdentityVerification verification, Year currentYear) {
        int level = TrustLevelPolicy.judge(true, verification.isAdult(currentYear), COMPANION_RECORD);
        return new TrustSummary(true, level);
    }

    private Year currentYear() {
        return Year.from(clock.instant().atZone(IdentityVerificationService.KOREA));
    }
}
