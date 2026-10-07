package com.pitchmap.basecamp.application;

import java.util.List;

/**
 * 검색 결과의 한 페이지다. 전체 개수는 세지 않고, 다음 페이지가 있는지만 hasNext로 알려 준다.
 *
 * @param content 출발일이 빠른 순서의 베이스캠프 목록
 * @param page 0부터 센 페이지 번호
 * @param size 요청한 페이지 크기
 */
public record BasecampSearchPage(List<BasecampSearchItem> content, int page, int size, boolean hasNext) {}
