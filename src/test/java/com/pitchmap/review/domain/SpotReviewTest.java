package com.pitchmap.review.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.testsupport.MutableClock;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SpotReviewTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;
    private static final LocalDate VISITED = LocalDate.of(2026, 10, 4);

    @Test
    @DisplayName("[F-10] 후기를 만들면 장소, 작성자, 방문일, 평점, 내용을 담고 작성·수정 시각은 만든 시각이다")
    void writeKeepsValues() {
        // when
        SpotReview review = SpotReview.write(11L, 7L, VISITED, 4, "물이 가까웠다", NOW);

        // then
        assertThat(review.getSpotId()).isEqualTo(11L);
        assertThat(review.getMemberId()).isEqualTo(7L);
        assertThat(review.getVisitedDate()).isEqualTo(VISITED);
        assertThat(review.getRating()).isEqualTo((short) 4);
        assertThat(review.getContent()).isEqualTo("물이 가까웠다");
        assertThat(review.getCreatedAt()).isEqualTo(NOW);
        assertThat(review.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("[F-10] 작성자 ID가 같을 때만 isWrittenBy가 true다")
    void isWrittenByComparesAuthorId() {
        // given
        SpotReview review = SpotReview.write(11L, 7L, VISITED, 4, "좋다", NOW);

        // then
        assertThat(review.isWrittenBy(7L)).isTrue();
        assertThat(review.isWrittenBy(8L)).isFalse();
    }

    @Test
    @DisplayName("[F-10] 후기를 고치면 평점, 내용, 수정 시각만 바뀌고 방문일과 작성 시각은 그대로다")
    void reviseChangesOnlyRatingContentAndUpdatedAt() {
        // given
        SpotReview review = SpotReview.write(11L, 7L, VISITED, 4, "좋다", NOW);
        Instant later = NOW.plusSeconds(3600);

        // when
        review.revise(2, "다시 보니 별로다", later);

        // then
        assertThat(review.getRating()).isEqualTo((short) 2);
        assertThat(review.getContent()).isEqualTo("다시 보니 별로다");
        assertThat(review.getUpdatedAt()).isEqualTo(later);
        assertThat(review.getVisitedDate()).isEqualTo(VISITED);
        assertThat(review.getCreatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6, -1})
    @DisplayName("[F-10] 평점이 1~5가 아니면 만들 때도 고칠 때도 거부한다")
    void rejectsRatingOutOfRange(int rating) {
        // given
        SpotReview review = SpotReview.write(11L, 7L, VISITED, 3, "좋다", NOW);

        // then
        assertThatThrownBy(() -> SpotReview.write(11L, 7L, VISITED, rating, "좋다", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> review.revise(rating, "좋다", NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[F-10] 내용이 null이거나 공백뿐이거나 2000자를 넘으면 거부하고, 2000자는 받는다")
    void validatesContent() {
        // then
        assertThatThrownBy(() -> SpotReview.write(11L, 7L, VISITED, 3, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SpotReview.write(11L, 7L, VISITED, 3, "  ", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SpotReview.write(11L, 7L, VISITED, 3, "가".repeat(2001), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(SpotReview.write(11L, 7L, VISITED, 3, "가".repeat(2000), NOW).getContent())
                .hasSize(2000);
    }

    @Test
    @DisplayName("[F-10] 방문일이나 시각이 null이면 거부한다")
    void rejectsNullDateOrTime() {
        assertThatThrownBy(() -> SpotReview.write(11L, 7L, null, 3, "좋다", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SpotReview.write(11L, 7L, VISITED, 3, "좋다", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
