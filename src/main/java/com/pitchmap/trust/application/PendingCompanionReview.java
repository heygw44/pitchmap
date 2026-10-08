package com.pitchmap.trust.application;

import java.time.Instant;
import java.util.List;

/**
 * 후기를 쓸 수 있는 베이스캠프 하나와, 아직 후기를 쓰지 않은 상대들. deadline은 작성 기한이고 그 시각까지 쓸 수 있다.
 * targets는 비어 있지 않다.
 */
public record PendingCompanionReview(
        long basecampId, String basecampTitle, Instant completedAt, Instant deadline, List<Target> targets) {

    /** 후기를 쓸 상대 한 명. */
    public record Target(long memberId, String nickname) {}
}
