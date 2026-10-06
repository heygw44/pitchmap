package com.pitchmap.review.application;

import java.util.List;

/**
 * 후기 목록의 한 페이지. 전체 개수는 세지 않고, 다음 페이지가 있는지만 {@code hasNext}로 알려 준다.
 *
 * @param content 작성 시각이 늦은 순서로 정렬한 후기 목록
 * @param page 0부터 센 페이지 번호
 * @param size 요청한 페이지 크기
 */
public record SpotReviewPage(List<SpotReviewItem> content, int page, int size, boolean hasNext) {}
