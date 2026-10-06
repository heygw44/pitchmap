package com.pitchmap.review.api;

import com.pitchmap.review.application.SpotReviewReviseCommand;
import com.pitchmap.review.domain.SpotReview;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 평점과 내용을 둘 다 받는다. 방문일은 고칠 수 없어서 받지 않는다.
public record SpotReviewUpdateRequest(
        @NotNull(message = "평점을 입력해야 합니다.")
        @Min(value = SpotReview.MIN_RATING, message = "평점은 1 이상이어야 합니다.")
        @Max(value = SpotReview.MAX_RATING, message = "평점은 5 이하여야 합니다.")
        Integer rating,

        @NotBlank(message = "후기 내용을 입력해야 합니다.")
        @Size(max = SpotReview.CONTENT_MAX_LENGTH, message = "후기 내용은 2000자 이하여야 합니다.")
        String content) {

    SpotReviewReviseCommand toCommand() {
        return new SpotReviewReviseCommand(rating, content);
    }
}
