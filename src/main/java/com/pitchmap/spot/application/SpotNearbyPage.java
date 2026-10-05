package com.pitchmap.spot.application;

import java.util.List;

/**
 * 반경 검색 결과의 한 페이지. 전체 개수는 세지 않고, 다음 페이지가 있는지만 {@code hasNext}로 알려 준다.
 *
 * @param content 가까운 장소부터 정렬한 장소 목록
 * @param page 0부터 센 페이지 번호
 * @param size 요청한 페이지 크기
 */
public record SpotNearbyPage(List<SpotNearbyItem> content, int page, int size, boolean hasNext) {}
