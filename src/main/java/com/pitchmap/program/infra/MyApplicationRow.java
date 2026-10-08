package com.pitchmap.program.infra;

import com.pitchmap.program.domain.ProgramApplicationStatus;
import java.time.Instant;

/** 로그인한 회원이 한 행사에 낸 가장 최근 신청이다. */
public record MyApplicationRow(long applicationId, ProgramApplicationStatus status, Instant paymentDueAt) {}
