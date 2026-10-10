package com.pitchmap.program.infra;

import com.pitchmap.program.domain.ProgramApplicationStatus;
import com.pitchmap.program.domain.ProgramCancelReason;
import java.time.Instant;

/** 관리자가 보는 신청자 목록의 한 행이다. 신청자의 이메일은 읽지 않고 닉네임만 읽는다. */
public record ProgramApplicantRow(
        long applicationId,
        long memberId,
        String nickname,
        ProgramApplicationStatus status,
        Instant paymentDueAt,
        Instant confirmedAt,
        Instant canceledAt,
        ProgramCancelReason cancelReason,
        Instant reviewRequestedAt,
        Instant createdAt) {}
