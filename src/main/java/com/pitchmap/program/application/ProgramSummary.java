package com.pitchmap.program.application;

import java.time.Instant;

/**
 * 공개 목록에 나오는 행사 한 건이다. status는 UPCOMING, OPEN, CLOSED, CANCELED 중 하나이고, spotId는 지도 장소와 연결하지 않았으면 null이다.
 */
public record ProgramSummary(
        long programId,
        String title,
        String locationText,
        Long spotId,
        Instant startAt,
        Instant endAt,
        Instant applyOpenAt,
        Instant applyCloseAt,
        int capacity,
        int remainingSeats,
        int fee,
        boolean overnight,
        String status) {}
