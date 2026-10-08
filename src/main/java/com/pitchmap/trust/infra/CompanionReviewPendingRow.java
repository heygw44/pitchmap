package com.pitchmap.trust.infra;

import java.time.Instant;

/** 작성할 후기 한 건: 베이스캠프와 아직 후기를 쓰지 않은 상대 한 명이다. */
public record CompanionReviewPendingRow(
        long basecampId, String basecampTitle, Instant completedAt, long targetId, String targetNickname) {}
