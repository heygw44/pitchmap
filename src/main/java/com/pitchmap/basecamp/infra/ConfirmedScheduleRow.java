package com.pitchmap.basecamp.infra;

import java.time.LocalDate;

/** 한 회원이 ACTIVE 멤버로 들어 있는 확정된 베이스캠프의 일정이다. */
public record ConfirmedScheduleRow(long basecampId, LocalDate startDate, LocalDate endDate) {}
