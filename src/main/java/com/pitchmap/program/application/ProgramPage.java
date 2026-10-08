package com.pitchmap.program.application;

import java.util.List;

/**
 * 공개 행사 목록의 한 페이지다. 전체 개수는 세지 않고, 다음 페이지가 있는지만 hasNext로 알려 준다.
 *
 * @param content 행사 시작 시각이 이른 순서의 행사 목록
 * @param page 0부터 센 페이지 번호
 * @param size 요청한 페이지 크기
 */
public record ProgramPage(List<ProgramSummary> content, int page, int size, boolean hasNext) {}
