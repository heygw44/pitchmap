package com.pitchmap.trust.infra;

import java.time.Instant;

/** 후기를 쓸 자격을 가릴 때 읽은 값. completedAt은 베이스캠프가 완료되기 전이면 null이다. */
public record CompanionReviewWriteContextRow(
        String basecampStatus,
        Instant completedAt,
        boolean reviewerActive,
        boolean revieweeActive,
        boolean alreadyWritten) {}
