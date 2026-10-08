package com.pitchmap.program.application;

import java.time.Instant;

/**
 * 관리자가 행사를 등록하는 요청 내용이다. spotId는 지도의 장소와 연결하지 않으면 null이고,
 * paymentDeadlineMinutes가 null이면 기본 결제 기한을 쓴다.
 */
public record ProgramCreateCommand(
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
        Integer paymentDeadlineMinutes,
        boolean overnight) {}
