package com.pitchmap.trust.application;

import com.pitchmap.trust.domain.CompanionReviewTag;
import java.time.Instant;
import java.util.List;

/**
 * 내가 받은 후기 한 건. revealed가 false이면 블라인드 공개 전이라 basecampId 외의 값은 모두 null이다.
 * 공개 전 후기의 내용은 서비스가 만들 때부터 담지 않는다.
 */
public record ReceivedCompanionReview(
        long basecampId,
        boolean revealed,
        Long reviewId,
        String basecampTitle,
        Long reviewerId,
        String reviewerNickname,
        Boolean rejoinWanted,
        List<CompanionReviewTag> tags,
        String comment,
        Instant createdAt) {

    static ReceivedCompanionReview sealed(long basecampId) {
        return new ReceivedCompanionReview(basecampId, false, null, null, null, null, null, null, null, null);
    }
}
