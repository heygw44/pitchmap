package com.pitchmap.trust.application;

import java.util.List;

/**
 * 회원 한 명의 신뢰 단계와 그 근거다. 본인확인으로 계산한 연령대·성별은 이름 문자열로 담고, 본인확인을 안 했거나
 * 미성년이라 연령대가 없으면 null이다. 출생연도 원값은 담지 않는다.
 *
 * @param rejoinRate "다시 동행" 비율(정수 %). 받은 후기가 없으면 null
 * @param topTags 받은 후기에서 많이 나온 태그. 후기 기능이 생기기 전에는 빈 목록이다
 */
public record TrustDetail(
        boolean identityVerified,
        boolean adult,
        int trustLevel,
        int completedCompanions,
        Integer rejoinRate,
        boolean noRecentSanction,
        List<String> topTags,
        String verifiedAgeGroup,
        String verifiedGender) {}
