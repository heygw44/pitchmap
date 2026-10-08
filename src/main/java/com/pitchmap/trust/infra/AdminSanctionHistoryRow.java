package com.pitchmap.trust.infra;

import java.time.Instant;

/** 회원의 제재 이력 한 건. level은 임시 정지이면 null이고, endsAt은 경고와 영구 정지이면 null이며, liftedAt은 해제되지 않았으면 null이다. */
public record AdminSanctionHistoryRow(
        long sanctionId,
        String type,
        Integer level,
        String status,
        String reason,
        Instant startsAt,
        Instant endsAt,
        Instant liftedAt) {}
