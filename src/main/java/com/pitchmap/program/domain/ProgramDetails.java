package com.pitchmap.program.domain;

import java.time.Instant;

/**
 * 행사를 만들 때 관리자가 정하는 내용이다. spotId는 지도의 장소와 연결하지 않으면 null이다.
 * 결제 기한은 신청한 시각부터 센 분 단위이고, 기본값은 {@link Program#DEFAULT_PAYMENT_DEADLINE_MINUTES}다.
 */
public record ProgramDetails(
        String title,
        String description,
        Long spotId,
        String locationText,
        Instant startAt,
        Instant endAt,
        int capacity,
        int fee,
        Instant applyOpenAt,
        Instant applyCloseAt,
        int paymentDeadlineMinutes,
        boolean overnight) {}
