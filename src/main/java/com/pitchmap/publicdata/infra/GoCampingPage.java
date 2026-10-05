package com.pitchmap.publicdata.infra;

import java.util.List;

/**
 * 고캠핑 목록 API 응답 한 페이지.
 *
 * @param totalCount 원천에 있는 캠핑장 전체 수
 * @param pageNo 응답한 페이지 번호. 1부터 센다.
 * @param numOfRows 요청한 페이지 크기
 * @param items 이 페이지의 캠핑장. 마지막 페이지를 넘으면 빈 목록이다.
 */
public record GoCampingPage(int totalCount, int pageNo, int numOfRows, List<GoCampingItem> items) {

    public GoCampingPage {
        items = List.copyOf(items);
    }
}
