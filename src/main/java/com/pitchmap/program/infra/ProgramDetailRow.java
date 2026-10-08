package com.pitchmap.program.infra;

import com.pitchmap.program.domain.ProgramStatus;
import java.time.Instant;

/** 행사 상세 한 건이다. 목록 항목에 설명과 결제 기한(분)이 더해진다. */
public record ProgramDetailRow(
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
        ProgramStatus status) {}
