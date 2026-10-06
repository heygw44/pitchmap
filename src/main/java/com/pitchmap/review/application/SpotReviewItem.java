package com.pitchmap.review.application;

import com.pitchmap.review.infra.SpotReviewRow;
import java.time.Instant;
import java.time.LocalDate;

/** 후기 한 건과 작성자. authorNickname은 조회한 시점의 닉네임이라서, 탈퇴한 회원이면 익명 닉네임이다. */
public record SpotReviewItem(
        long reviewId,
        long authorId,
        String authorNickname,
        LocalDate visitedDate,
        int rating,
        String content,
        Instant createdAt) {

    static SpotReviewItem from(SpotReviewRow row) {
        return new SpotReviewItem(
                row.reviewId(),
                row.authorId(),
                row.authorNickname(),
                row.visitedDate(),
                row.rating(),
                row.content(),
                row.createdAt());
    }
}
