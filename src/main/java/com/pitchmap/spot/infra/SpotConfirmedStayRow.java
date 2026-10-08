package com.pitchmap.spot.infra;

import java.time.LocalDate;

/** 확정된 베이스캠프 하나의 야영 기간과 현재 인원. 종료일은 야영하는 밤에 들어가지 않는다. */
public record SpotConfirmedStayRow(LocalDate startDate, LocalDate endDate, int headcount) {}
