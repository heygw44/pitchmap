package com.pitchmap.trust.application;

import com.pitchmap.trust.domain.CompanionReviewTag;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

// 조회 SQL은 후기의 태그를 이름을 쉼표로 이은 문자열 하나로 읽어 온다. 이 클래스가 그 문자열을 열거형 목록으로 되돌린다.
final class CompanionReviewTags {

    private CompanionReviewTags() {}

    /** 호출하면 쉼표로 이은 태그 이름을 열거형 선언 순서의 목록으로 돌려준다. 태그가 없으면(null) 빈 목록이다. */
    static List<CompanionReviewTag> parse(String joined) {
        if (joined == null || joined.isEmpty()) {
            return List.of();
        }
        Set<CompanionReviewTag> tags = EnumSet.noneOf(CompanionReviewTag.class);
        Arrays.stream(joined.split(",")).map(CompanionReviewTag::valueOf).forEach(tags::add);
        return List.copyOf(tags);
    }
}
