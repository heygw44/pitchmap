package com.pitchmap.program.application;

import com.pitchmap.program.domain.ProgramApplicationStatus;

/**
 * 내 행사 신청 목록의 조회 조건이다.
 *
 * @param status 보려는 신청 상태. null이면 상태로 거르지 않는다
 * @param page 0부터 센 페이지 번호
 * @param size 페이지 크기
 */
public record MyProgramApplicationQuery(ProgramApplicationStatus status, int page, int size) {}
