package com.pitchmap.trust.application;

import com.pitchmap.member.application.MemberProfile;
import com.pitchmap.member.application.MemberProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberProfileQueryService {

    private final MemberProfileService memberProfileService;
    private final TrustSummaryService trustSummaryService;

    /** 호출하면 회원 모듈의 닉네임·자기 신고 값과 신뢰 모듈의 신뢰 정보를 합쳐 프로필을 돌려준다. 없는 회원이면 NOT_FOUND로 실패한다. */
    public MemberTrustProfile find(long memberId) {
        MemberProfile profile = memberProfileService.find(memberId);
        TrustDetail detail = trustSummaryService.detail(memberId);
        return new MemberTrustProfile(
                profile.memberId(),
                profile.nickname(),
                firstNonNull(detail.verifiedAgeGroup(), profile.selfAgeGroup()),
                detail.verifiedAgeGroup() != null,
                firstNonNull(detail.verifiedGender(), profile.selfGender()),
                detail.verifiedGender() != null,
                detail.trustLevel(),
                detail.completedCompanions(),
                detail.rejoinRate(),
                detail.topTags());
    }

    // 본인확인한 값이 있으면 그 값을 쓰고, 없을 때만 자기 신고 값(없으면 null)을 쓴다.
    private static String firstNonNull(String verified, String selfReported) {
        if (verified != null) {
            return verified;
        }
        return selfReported;
    }
}
