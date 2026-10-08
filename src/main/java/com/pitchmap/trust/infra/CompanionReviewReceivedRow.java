package com.pitchmap.trust.infra;

import java.time.Instant;

/**
 * 받은 후기 한 건. tags는 태그 이름을 쉼표로 이은 문자열이고 태그가 없으면 null이다.
 * reverseWritten은 받은 회원이 그 작성자에게 같은 베이스캠프의 후기를 썼는지다.
 */
public record CompanionReviewReceivedRow(
        long reviewId,
        long basecampId,
        String basecampTitle,
        Instant completedAt,
        long reviewerId,
        String reviewerNickname,
        boolean rejoinWanted,
        String tags,
        String comment,
        Instant createdAt,
        boolean reverseWritten) {}
