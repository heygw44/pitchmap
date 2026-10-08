package com.pitchmap.trust.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CompanionReviewTagTest {

    @Test
    @DisplayName("[F-15] 대표 태그는 많이 나온 순서로 최대 3개만 고른다")
    void topIsLimitedToThree() {
        Map<CompanionReviewTag, Long> counts = new EnumMap<>(CompanionReviewTag.class);
        counts.put(CompanionReviewTag.ON_TIME, 1L);
        counts.put(CompanionReviewTag.LEAVE_NO_TRACE, 4L);
        counts.put(CompanionReviewTag.CONSIDERATE, 3L);
        counts.put(CompanionReviewTag.WELL_PREPARED, 2L);
        counts.put(CompanionReviewTag.LATE, 5L);

        List<CompanionReviewTag> top = CompanionReviewTag.top(counts);

        assertThat(top)
                .containsExactly(
                        CompanionReviewTag.LATE, CompanionReviewTag.LEAVE_NO_TRACE, CompanionReviewTag.CONSIDERATE);
    }

    @Test
    @DisplayName("[F-15] 횟수가 같으면 태그 선언 순서가 앞선 태그가 먼저다")
    void tiesFollowDeclarationOrder() {
        Map<CompanionReviewTag, Long> counts = new EnumMap<>(CompanionReviewTag.class);
        counts.put(CompanionReviewTag.LITTERING, 2L);
        counts.put(CompanionReviewTag.CONSIDERATE, 2L);
        counts.put(CompanionReviewTag.ON_TIME, 2L);
        counts.put(CompanionReviewTag.NO_SHOW, 2L);

        assertThat(CompanionReviewTag.top(counts))
                .containsExactly(
                        CompanionReviewTag.ON_TIME, CompanionReviewTag.CONSIDERATE, CompanionReviewTag.NO_SHOW);
    }

    @Test
    @DisplayName("[F-15] 횟수 맵이 비었거나 0회뿐이면 빈 목록이다")
    void emptyOrZeroCountsGiveEmptyList() {
        assertThat(CompanionReviewTag.top(Map.of())).isEmpty();
        assertThat(CompanionReviewTag.top(Map.of(CompanionReviewTag.ON_TIME, 0L)))
                .isEmpty();
    }
}
