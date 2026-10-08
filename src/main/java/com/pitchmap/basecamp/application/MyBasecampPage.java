package com.pitchmap.basecamp.application;

import java.util.List;

/**
 * 내 베이스캠프 목록의 한 페이지다. 전체 개수는 세지 않고, 다음 페이지가 있는지만 hasNext로 알려 준다.
 *
 * @param content 출발일이 늦은 순서의 베이스캠프 목록
 * @param page 0부터 센 페이지 번호
 * @param size 요청한 페이지 크기
 */
public record MyBasecampPage(List<MyBasecampItem> content, int page, int size, boolean hasNext) {}
