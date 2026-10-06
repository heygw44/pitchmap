package com.pitchmap.trust.application;

import java.util.List;

/**
 * 로그인한 회원에게 보여 줄 다른 회원의 프로필이다. 연령대·성별은 본인확인한 값이 있으면 그 값(verified true),
 * 없으면 회원이 직접 밝힌 값(verified false, 밝히지 않았으면 값이 null)이다.
 */
public record MemberTrustProfile(
        long memberId,
        String nickname,
        String ageGroup,
        boolean ageGroupVerified,
        String gender,
        boolean genderVerified,
        int trustLevel,
        int completedCompanions,
        Integer rejoinRate,
        List<String> topTags) {}
