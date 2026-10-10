package com.pitchmap.community.api;

import com.pitchmap.community.application.CommunityPostListQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

// 화면이 쿼리 파라미터로 보내는 조건이다. 값이 없으면 기본값을 쓰거나 거르지 않으려고 래퍼 타입으로 받는다.
public record CommunityPostListRequest(
        Long spotId,

        @Schema(description = "true면 좋아요를 5개 이상 받은 인기글만 준다.")
        Boolean popular,

        @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.") Integer page,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.") @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.")
        Integer size) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    CommunityPostListQuery toQuery() {
        return new CommunityPostListQuery(spotId, Boolean.TRUE.equals(popular));
    }

    int pageOrDefault() {
        return page == null ? DEFAULT_PAGE : page;
    }

    int sizeOrDefault() {
        return size == null ? DEFAULT_SIZE : size;
    }
}
