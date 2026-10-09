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
        return merge(memberProfileService.find(memberId));
    }

    /**
     * 호출하면 {@link #find}와 같은 프로필을 돌려주되 탈퇴한 회원도 포함한다. 베이스캠프 멤버 목록처럼 다른 화면 안에 회원이 끼어 보이는 곳에서 쓴다.
     * 탈퇴한 회원은 닉네임이 {@code 탈퇴회원_{id}}이고 연령대와 성별이 비어 있다.
     */
    public MemberTrustProfile findIncludingWithdrawn(long memberId) {
        return merge(memberProfileService.findIncludingWithdrawn(memberId));
    }

    private MemberTrustProfile merge(MemberProfile profile) {
        long memberId = profile.memberId();
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
