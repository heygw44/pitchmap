package com.pitchmap.basecamp.application;

import java.util.List;

/**
 * 합류 신청 목록의 한 페이지다. 전체 개수는 세지 않고, 다음 페이지가 있는지만 hasNext로 알려 준다.
 *
 * @param content 신청이 오래된 순서의 신청 목록
 * @param page 0부터 센 페이지 번호
 * @param size 요청한 페이지 크기
 */
public record BasecampApplicationPage(List<BasecampApplicationItem> content, int page, int size, boolean hasNext) {}
