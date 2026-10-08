package com.pitchmap.program.application;

import com.pitchmap.program.domain.ProgramPhase;

/**
 * 공개 행사 목록의 조회 조건이다.
 *
 * @param phase 보려는 진행 단계. UPCOMING, OPEN, CLOSED 중 하나이고 null이면 단계로 거르지 않는다
 * @param page 0부터 센 페이지 번호
 * @param size 페이지 크기
 */
public record ProgramListQuery(ProgramPhase phase, int page, int size) {}
