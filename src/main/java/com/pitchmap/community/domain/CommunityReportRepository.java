package com.pitchmap.community.domain;

import java.time.Instant;

public interface CommunityReportRepository {

    CommunityReport saveAndFlush(CommunityReport report);

    boolean existsByTargetTypeAndTargetIdAndReporterId(
            CommunityReportTargetType targetType, long targetId, long reporterId);

    /** 검토 전(reviewedAt이 null인) 신고의 수를 센다. */
    long countByTargetTypeAndTargetIdAndReviewedAtIsNull(CommunityReportTargetType targetType, long targetId);

    /**
     * 호출하면 그 대상의 검토 전 신고를 모두 now에 검토를 마친 것으로 표시하고 표시한 건수를 돌려준다. 호출하는 쪽이 같은 트랜잭션에서
     * 바꾼 글이나 댓글 엔티티가 있으면 먼저 플러시한 뒤 실행한다.
     */
    int markReviewed(CommunityReportTargetType targetType, long targetId, Instant now);
}
