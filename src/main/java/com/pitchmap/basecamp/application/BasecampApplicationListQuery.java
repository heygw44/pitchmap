package com.pitchmap.basecamp.application;

import com.pitchmap.basecamp.domain.BasecampApplicationStatus;

/**
 * 합류 신청 목록의 조회 조건이다.
 *
 * @param status 보려는 신청 상태
 * @param page 0부터 센 페이지 번호
 * @param size 페이지 크기
 */
public record BasecampApplicationListQuery(BasecampApplicationStatus status, int page, int size) {}
