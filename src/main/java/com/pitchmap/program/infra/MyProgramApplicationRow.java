package com.pitchmap.program.infra;

import com.pitchmap.program.domain.ProgramApplicationStatus;
import com.pitchmap.program.domain.ProgramCancelReason;
import com.pitchmap.program.domain.ProgramStatus;
import java.time.Instant;

/** 내 행사 신청 목록의 한 행이다. 신청 정보와 신청한 행사의 요약을 함께 읽고, 회원 정보는 읽지 않는다. */
public record MyProgramApplicationRow(
        long applicationId,
        ProgramApplicationStatus status,
        Instant paymentDueAt,
        Instant confirmedAt,
        Instant canceledAt,
        ProgramCancelReason cancelReason,
        Instant createdAt,
        long programId,
        String title,
        String locationText,
        Instant startAt,
        Instant endAt,
        int fee,
        ProgramStatus programStatus,
        Instant applyOpenAt,
        Instant applyCloseAt) {}
