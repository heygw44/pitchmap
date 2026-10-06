package com.pitchmap.trust.api;

import com.pitchmap.trust.application.MemberTrustProfile;
import java.util.List;

/**
 * 회원 프로필 응답이다. 요청자가 로그인하지 않았으면 닉네임만, 로그인했으면 연령대·성별·신뢰 정보까지 담는다.
 * 권한이 없는 필드는 null로 두지 않고 필드 자체를 뺀다.
 */
public sealed interface MemberProfileResponse {

    /** 비로그인 요청자가 보는 프로필이다. */
    record Anonymous(long memberId, String nickname) implements MemberProfileResponse {}

    /**
     * 로그인한 회원이 보는 프로필이다. 연령대·성별이 없으면 null이고, 본인확인한 값이면 verified가 true다.
     */
    record Member(
            long memberId,
            String nickname,
            String ageGroup,
            boolean ageGroupVerified,
            String gender,
            boolean genderVerified,
            int trustLevel,
            int completedCompanions,
            CompanionReviewSummary companionReviewSummary)
            implements MemberProfileResponse {

        static Member from(MemberTrustProfile profile) {
            return new Member(
                    profile.memberId(),
                    profile.nickname(),
                    profile.ageGroup(),
                    profile.ageGroupVerified(),
                    profile.gender(),
                    profile.genderVerified(),
                    profile.trustLevel(),
                    profile.completedCompanions(),
                    new CompanionReviewSummary(profile.rejoinRate(), profile.topTags()));
        }
    }

    /** 받은 동행 후기 요약이다. 받은 후기가 없으면 rejoinRate는 null이고 topTags는 빈 목록이다. */
    record CompanionReviewSummary(Integer rejoinRate, List<String> topTags) {}
}
