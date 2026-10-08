package com.pitchmap.trust.api;

import com.pitchmap.trust.application.PublicCompanionReview;
import com.pitchmap.trust.domain.CompanionReviewTag;
import java.time.Instant;
import java.util.List;

/** 다른 회원이 보는 받은 후기 한 건이다. 작성자와 개별 "다시 동행" 여부는 담지 않는다. comment가 없으면 null이다. */
public record PublicCompanionReviewResponse(List<CompanionReviewTag> tags, String comment, Instant createdAt) {

    static PublicCompanionReviewResponse from(PublicCompanionReview review) {
        return new PublicCompanionReviewResponse(review.tags(), review.comment(), review.createdAt());
    }
}
