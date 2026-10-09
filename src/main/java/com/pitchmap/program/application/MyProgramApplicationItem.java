package com.pitchmap.program.application;

import java.time.Instant;

/**
 * 내 행사 신청 한 건이다. confirmedAt, canceledAt, cancelReason은 해당 상태가 아니면 null이다.
 * program의 fee는 결제한 금액이 아니라 지금의 참가비이고, program의 status는 UPCOMING, OPEN, CLOSED, CANCELED 중 하나다.
 */
public record MyProgramApplicationItem(
        long applicationId,
        String status,
        Instant paymentDueAt,
        Instant confirmedAt,
        Instant canceledAt,
        String cancelReason,
        Instant createdAt,
        Program program) {

    public record Program(
            long programId,
            String title,
            String locationText,
            Instant startAt,
            Instant endAt,
            int fee,
            String status) {}
}
