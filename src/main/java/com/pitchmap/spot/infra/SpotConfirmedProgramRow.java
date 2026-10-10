package com.pitchmap.spot.infra;

import java.time.Instant;

/** 숙박 공식 행사 하나의 시작·종료 시각(UTC)과 확정된 참가 신청 수. 야영하는 밤으로 바꾸는 일은 호출하는 쪽이 한국 날짜로 한다. */
public record SpotConfirmedProgramRow(Instant startAt, Instant endAt, int headcount) {}
