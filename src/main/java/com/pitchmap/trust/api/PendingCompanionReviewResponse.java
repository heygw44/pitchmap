package com.pitchmap.trust.api;

import com.pitchmap.trust.application.PendingCompanionReview;
import java.time.Instant;
import java.util.List;

/** 후기를 쓸 수 있는 베이스캠프 하나. deadline까지 쓸 수 있고, targets는 아직 후기를 쓰지 않은 상대다. */
public record PendingCompanionReviewResponse(
        long basecampId, String basecampTitle, Instant completedAt, Instant deadline, List<TargetResponse> targets) {

    static PendingCompanionReviewResponse from(PendingCompanionReview pending) {
        List<TargetResponse> targets = pending.targets().stream()
                .map(target -> new TargetResponse(target.memberId(), target.nickname()))
                .toList();
        return new PendingCompanionReviewResponse(
                pending.basecampId(), pending.basecampTitle(), pending.completedAt(), pending.deadline(), targets);
    }

    public record TargetResponse(long memberId, String nickname) {}
}
