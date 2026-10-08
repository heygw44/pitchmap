package com.pitchmap.spot.application;

import java.util.List;

/**
 * 관리자 박지 검토 목록의 한 페이지. 전체 개수는 세지 않고, 다음 페이지가 있는지만 {@code hasNext}로 알려 준다.
 *
 * @param page 0부터 센 페이지 번호
 * @param size 요청한 페이지 크기
 */
public record AdminSpotPage(List<AdminSpotSummary> content, int page, int size, boolean hasNext) {}
