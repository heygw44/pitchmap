package com.pitchmap.trust.infra;

import java.time.Instant;

/**
 * 신고 자격을 가릴 때 읽은 값. completedAt은 베이스캠프가 완료되기 전이면 null이다.
 * reviewMatches와 reverseWritten은 후기 신고가 아니면 의미가 없다.
 */
public record MemberReportContextRow(
        boolean reporterInvolved,
        boolean targetInvolved,
        boolean alreadyReported,
        boolean reviewMatches,
        boolean reverseWritten,
        Instant completedAt) {}
