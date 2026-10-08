package com.pitchmap.program.application;

/**
 * 관리자가 보는 신청자 목록의 조회 조건이다.
 *
 * @param status 보려는 신청 상태 이름(PENDING_PAYMENT, CONFIRMED, CANCELED, EXPIRED). null이면 상태로 거르지 않는다
 * @param page 0부터 센 페이지 번호
 * @param size 페이지 크기
 */
public record ProgramApplicantQuery(String status, int page, int size) {}
