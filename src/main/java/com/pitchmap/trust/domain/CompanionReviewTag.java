package com.pitchmap.trust.domain;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** 동행 후기에 붙이는 태그. 앞의 넷은 긍정, 뒤의 셋은 부정이다. */
public enum CompanionReviewTag {
    ON_TIME,
    LEAVE_NO_TRACE,
    CONSIDERATE,
    WELL_PREPARED,
    LATE,
    NO_SHOW,
    LITTERING;

    /** 프로필에 보여 줄 대표 태그의 최대 개수다. */
    public static final int TOP_TAG_LIMIT = 3;

    /**
     * 호출하면 태그별 횟수에서 많이 나온 순서로 최대 3개를 돌려준다. 횟수가 같으면 선언 순서가 앞선 태그가 먼저이고,
     * 횟수가 0 이하인 태그는 뺀다.
     */
    public static List<CompanionReviewTag> top(Map<CompanionReviewTag, Long> counts) {
        return Arrays.stream(values())
                .filter(tag -> counts.getOrDefault(tag, 0L) > 0)
                .sorted(Comparator.comparing((CompanionReviewTag tag) -> counts.get(tag))
                        .reversed()
                        .thenComparing(Enum::ordinal))
                .limit(TOP_TAG_LIMIT)
                .toList();
    }
}
