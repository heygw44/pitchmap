package com.pitchmap.review.api;

import com.pitchmap.review.application.SpotReviewItem;
import java.time.Instant;
import java.time.LocalDate;

// 작성자는 회원 ID와 닉네임만 내보낸다. 이메일 같은 다른 회원 정보는 응답에 넣지 않는다.
public record SpotReviewItemResponse(
        long reviewId, Author author, LocalDate visitedDate, int rating, String content, Instant createdAt) {

    static SpotReviewItemResponse from(SpotReviewItem item) {
        return new SpotReviewItemResponse(
                item.reviewId(),
                new Author(item.authorId(), item.authorNickname()),
                item.visitedDate(),
                item.rating(),
                item.content(),
                item.createdAt());
    }

    public record Author(long memberId, String nickname) {}
}
