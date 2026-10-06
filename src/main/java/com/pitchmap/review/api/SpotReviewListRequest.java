package com.pitchmap.review.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

// 화면이 쿼리 파라미터로 보내는 페이지 조건이다. 값이 없으면 기본값을 쓰려고 래퍼 타입으로 받는다.
public record SpotReviewListRequest(
        @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.") Integer page,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.") @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.")
        Integer size) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    int pageOrDefault() {
        return page == null ? DEFAULT_PAGE : page;
    }

    int sizeOrDefault() {
        return size == null ? DEFAULT_SIZE : size;
    }
}
