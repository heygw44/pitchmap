package com.pitchmap.trust.application;

import com.pitchmap.trust.domain.CompanionRecord;
import com.pitchmap.trust.domain.CompanionReviewPolicy;
import com.pitchmap.trust.domain.CompanionReviewTag;
import com.pitchmap.trust.domain.IdentityVerification;
import com.pitchmap.trust.domain.IdentityVerificationRepository;
import com.pitchmap.trust.domain.TrustLevelPolicy;
import com.pitchmap.trust.infra.TagCountRow;
import com.pitchmap.trust.infra.TrustRecordMapper;
import com.pitchmap.trust.infra.TrustRecordRow;
import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TrustSummaryService {

    private static final TrustSummary NOT_VERIFIED_LEVEL_ZERO = new TrustSummary(false, 0);

    private final IdentityVerificationRepository identityVerificationRepository;
    private final TrustRecordMapper trustRecordMapper;
    private final Clock clock;

    /**
     * 호출하면 회원의 본인확인 여부와 신뢰 단계를 계산해 돌려준다. 본인확인 기록이 없으면 본인확인 전, 단계 0이다.
     * 단계 2 판정은 본인확인을 마친 성인에게만 필요하므로, 그 밖의 회원은 동행 기록을 조회하지 않는다.
     */
    @Transactional(readOnly = true)
    public TrustSummary summarize(long memberId) {
        Instant now = clock.instant();
        return identityVerificationRepository
                .findByMemberId(memberId)
                .map(verification -> summarizeOf(verification, memberId, now))
                .orElse(NOT_VERIFIED_LEVEL_ZERO);
    }

    /**
     * 호출하면 신뢰 단계와 그 근거(완료 동행, "다시 동행" 비율, 최근 임박 탈퇴, 제재 여부, 대표 태그)와 본인확인한
     * 연령대·성별을 돌려준다. 연령대는 본인확인한 성인에게만 있고, 성별은 본인확인한 회원이면 미성년이어도 있다.
     * 프로필에 동행 기록을 보여 주므로, 본인확인 여부와 상관없이 동행 기록을 항상 조회한다.
     */
    @Transactional(readOnly = true)
    public TrustDetail detail(long memberId) {
        Instant now = clock.instant();
        Year currentYear = currentYear(now);
        Optional<IdentityVerification> found = identityVerificationRepository.findByMemberId(memberId);
        boolean adult =
                found.map(verification -> verification.isAdult(currentYear)).orElse(false);
        CompanionRecord record = companionRecordOf(memberId, now);
        int trustLevel = TrustLevelPolicy.judge(found.isPresent(), adult, record);
        String verifiedAgeGroup = found.flatMap(verification -> verification.ageGroupIn(currentYear))
                .map(Enum::name)
                .orElse(null);
        String verifiedGender =
                found.map(verification -> verification.getGender().name()).orElse(null);
        return new TrustDetail(
                found.isPresent(),
                adult,
                trustLevel,
                record.completedCompanions(),
                record.rejoinRate(),
                record.recentEarlyLeaves(),
                !record.recentSanction(),
                topTagsOf(memberId, now),
                verifiedAgeGroup,
                verifiedGender);
    }

    // 본인확인을 마친 성인이 단계 1이고, 동행 기록이 단계 2 조건을 채우면 단계 2다. 성인 여부는 본인확인 기록이 한 곳에서 판정한다.
    // 성인이 아니면 동행 기록과 상관없이 단계 0이라서 기록을 읽지 않는다.
    TrustSummary summarizeOf(IdentityVerification verification, long memberId, Instant now) {
        if (!verification.isAdult(currentYear(now))) {
            return new TrustSummary(true, TrustLevelPolicy.judge(true, false, CompanionRecord.NONE));
        }
        int level = TrustLevelPolicy.judge(true, true, companionRecordOf(memberId, now));
        return new TrustSummary(true, level);
    }

    // 제재 기록은 제재 기능에서 생긴다. 그 전까지는 모든 회원이 최근 확정 제재가 없는 것으로 계산한다.
    private CompanionRecord companionRecordOf(long memberId, Instant now) {
        TrustRecordRow row = trustRecordMapper.selectCompanionRecord(
                memberId, CompanionReviewPolicy.revealCutoff(now), now.minus(CompanionRecord.RECENT_PERIOD));
        return new CompanionRecord(
                Math.toIntExact(row.completedCompanions()),
                Math.toIntExact(row.receivedReviews()),
                Math.toIntExact(row.rejoinWanted()),
                Math.toIntExact(row.recentEarlyLeaves()),
                false);
    }

    private List<String> topTagsOf(long memberId, Instant now) {
        Map<CompanionReviewTag, Long> counts = new EnumMap<>(CompanionReviewTag.class);
        for (TagCountRow row :
                trustRecordMapper.selectRevealedTagCounts(memberId, CompanionReviewPolicy.revealCutoff(now))) {
            counts.put(CompanionReviewTag.valueOf(row.tag()), row.count());
        }
        return CompanionReviewTag.top(counts).stream().map(Enum::name).toList();
    }

    private static Year currentYear(Instant now) {
        return Year.from(now.atZone(IdentityVerificationService.KOREA));
    }
}
