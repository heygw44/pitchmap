package com.pitchmap.review.api;

import com.pitchmap.review.application.SpotReviewWriteCommand;
import com.pitchmap.review.domain.SpotReview;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

// 빠진 값을 @NotNull 위반으로 돌려주려고 평점은 래퍼 타입으로 받는다. 방문일이 오늘 뒤인지는 시각이 필요해서 서비스가 검사한다.
public record SpotReviewCreateRequest(
        @Schema(description = "방문일(한국 날짜). 오늘보다 뒤일 수 없다.", example = "2026-10-05") @NotNull(message = "방문일을 입력해야 합니다.")
        LocalDate visitedDate,

        @NotNull(message = "평점을 입력해야 합니다.")
        @Min(value = SpotReview.MIN_RATING, message = "평점은 1 이상이어야 합니다.")
        @Max(value = SpotReview.MAX_RATING, message = "평점은 5 이하여야 합니다.")
        Integer rating,

        @NotBlank(message = "후기 내용을 입력해야 합니다.")
        @Size(max = SpotReview.CONTENT_MAX_LENGTH, message = "후기 내용은 2000자 이하여야 합니다.")
        String content) {

    SpotReviewWriteCommand toCommand() {
        return new SpotReviewWriteCommand(visitedDate, rating, content);
    }
}
