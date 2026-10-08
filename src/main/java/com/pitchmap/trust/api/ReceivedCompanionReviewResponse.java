package com.pitchmap.trust.api;

import com.pitchmap.trust.application.ReceivedCompanionReview;
import com.pitchmap.trust.domain.CompanionReviewTag;
import java.time.Instant;
import java.util.List;

/**
 * 받은 후기 한 건의 응답이다. 블라인드 공개 전인 후기는 basecampId와 revealed(false)만 담고, 나머지 필드는 null로 두지 않고 필드 자체를 뺀다.
 * 공개된 후기의 comment가 없으면 null이다.
 */
public sealed interface ReceivedCompanionReviewResponse {

    /** 공개된 후기다. */
    record Revealed(
            long reviewId,
            long basecampId,
            String basecampTitle,
            ReviewerResponse reviewer,
            boolean rejoinWanted,
            List<CompanionReviewTag> tags,
            String comment,
            Instant createdAt,
            boolean revealed)
            implements ReceivedCompanionReviewResponse {}

    /** 블라인드 공개 전인 후기다. 어느 베이스캠프의 후기인지만 알려 준다. */
    record Sealed(long basecampId, boolean revealed) implements ReceivedCompanionReviewResponse {}

    /** 후기 작성자다. */
    record ReviewerResponse(long memberId, String nickname) {}

    static ReceivedCompanionReviewResponse from(ReceivedCompanionReview review) {
        if (!review.revealed()) {
            return new Sealed(review.basecampId(), false);
        }
        return new Revealed(
                review.reviewId(),
                review.basecampId(),
                review.basecampTitle(),
                new ReviewerResponse(review.reviewerId(), review.reviewerNickname()),
                review.rejoinWanted(),
                review.tags(),
                review.comment(),
                review.createdAt(),
                true);
    }
}
