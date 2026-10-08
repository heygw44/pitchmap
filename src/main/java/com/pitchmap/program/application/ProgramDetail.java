package com.pitchmap.program.application;

import java.time.Instant;

/**
 * 행사 상세다. myApplication은 로그인한 회원에게 신청이 있을 때만 값이 있고, 아니면 null이다.
 */
public record ProgramDetail(
        long programId,
        String title,
        String description,
        String locationText,
        Long spotId,
        Instant startAt,
        Instant endAt,
        Instant applyOpenAt,
        Instant applyCloseAt,
        int capacity,
        int remainingSeats,
        int fee,
        int paymentDeadlineMinutes,
        boolean overnight,
        String status,
        MyApplication myApplication) {

    /** 요청한 회원이 이 행사에 낸 가장 최근 신청이다. */
    public record MyApplication(long applicationId, String status, Instant paymentDueAt) {}
}
