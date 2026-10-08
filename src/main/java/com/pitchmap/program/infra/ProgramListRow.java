package com.pitchmap.program.infra;

import com.pitchmap.program.domain.ProgramStatus;
import java.time.Instant;

/** 공개 목록의 행사 한 건이다. remainingSeats는 정원에서 결제 대기·확정 신청 수를 뺀 값이고 0보다 작아지지 않는다. */
public record ProgramListRow(
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
        ProgramStatus status) {}
