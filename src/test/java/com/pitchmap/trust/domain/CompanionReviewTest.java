package com.pitchmap.trust.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CompanionReviewTest {

    private static final Instant NOW = Instant.parse("2026-10-05T03:00:00Z");

    @Test
    @DisplayName("[RV-04] 후기를 만들면 작성자, 상대, 다시 동행 여부, 태그, 코멘트, 작성 시각을 담는다")
    void writeKeepsValues() {
        CompanionReview review = CompanionReview.write(
                3L,
                10L,
                11L,
                true,
                List.of(CompanionReviewTag.ON_TIME, CompanionReviewTag.CONSIDERATE),
                "함께 해서 좋았다",
                NOW);

        assertThat(review.getBasecampId()).isEqualTo(3L);
        assertThat(review.getReviewerId()).isEqualTo(10L);
        assertThat(review.getRevieweeId()).isEqualTo(11L);
        assertThat(review.isRejoinWanted()).isTrue();
        assertThat(review.getTags())
                .containsExactlyInAnyOrder(CompanionReviewTag.ON_TIME, CompanionReviewTag.CONSIDERATE);
        assertThat(review.getComment()).isEqualTo("함께 해서 좋았다");
        assertThat(review.getCreatedAt()).isEqualTo(NOW);
        assertThat(review.getHiddenAt()).isNull();
    }

    @Test
    @DisplayName("[RV-04] 같은 태그를 두 번 넘겨도 한 번만 담고, 태그가 null이거나 비어도 만든다")
    void duplicateTagsAreStoredOnce() {
        CompanionReview duplicated = CompanionReview.write(
                3L, 10L, 11L, false, List.of(CompanionReviewTag.LATE, CompanionReviewTag.LATE), null, NOW);
        CompanionReview withoutTags = CompanionReview.write(3L, 10L, 11L, false, null, null, NOW);

        assertThat(duplicated.getTags()).containsExactly(CompanionReviewTag.LATE);
        assertThat(withoutTags.getTags()).isEmpty();
    }

    @Test
    @DisplayName("[RV-04] 공백뿐인 코멘트는 없는 것으로 저장하고, 300자는 받고 301자는 거부한다")
    void commentIsNormalizedAndBounded() {
        CompanionReview blank = CompanionReview.write(3L, 10L, 11L, true, List.of(), "   ", NOW);
        CompanionReview atLimit = CompanionReview.write(3L, 10L, 11L, true, List.of(), "가".repeat(300), NOW);

        assertThat(blank.getComment()).isNull();
        assertThat(atLimit.getComment()).hasSize(300);
        assertThatThrownBy(() -> CompanionReview.write(3L, 10L, 11L, true, List.of(), "가".repeat(301), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[RV-01] 자기 자신에게 쓰는 후기와 시각이 없는 후기는 만들 수 없다")
    void rejectsSelfReviewAndNullTime() {
        assertThatThrownBy(() -> CompanionReview.write(3L, 10L, 10L, true, List.of(), null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CompanionReview.write(3L, 10L, 11L, true, List.of(), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[SN-07] 후기를 숨기면 숨긴 시각이 남고, 이미 숨긴 후기는 처음 숨긴 시각을 그대로 둔다")
    void hideKeepsFirstHiddenTime() {
        CompanionReview review = CompanionReview.write(3L, 10L, 11L, true, List.of(), "코멘트", NOW);
        Instant later = NOW.plusSeconds(60);

        review.hide(NOW.plusSeconds(10));
        review.hide(later);

        assertThat(review.getHiddenAt()).isEqualTo(NOW.plusSeconds(10));
        assertThatThrownBy(() -> review.hide(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
