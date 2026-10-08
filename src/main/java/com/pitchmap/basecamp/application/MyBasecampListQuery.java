package com.pitchmap.basecamp.application;

import com.pitchmap.basecamp.domain.BasecampRelation;
import com.pitchmap.basecamp.domain.BasecampStatus;

/**
 * 내 베이스캠프 목록의 조회 조건이다.
 *
 * @param relation 보려는 관계(LEADER, MEMBER, APPLICANT). null이면 셋 모두 본다.
 * @param status 보려는 베이스캠프 상태. null이면 모든 상태를 본다.
 * @param page 0부터 센 페이지 번호
 * @param size 페이지 크기
 */
public record MyBasecampListQuery(BasecampRelation relation, BasecampStatus status, int page, int size) {}
