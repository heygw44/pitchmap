package com.pitchmap.community.api;

import com.pitchmap.community.application.CommunityPostListQuery;
import com.pitchmap.community.application.CommunitySearchType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

// 화면이 쿼리 파라미터로 보내는 조건이다. 값이 없으면 기본값을 쓰거나 거르지 않으려고 래퍼 타입으로 받는다.
public record CommunityPostListRequest(
        Long spotId,

        @Schema(description = "true면 좋아요를 5개 이상 받은 인기글만 준다.")
        Boolean popular,

        @Schema(description = "검색 대상. keyword가 없으면 무시하고, 보내지 않으면 제목과 본문을 찾는다.")
        CommunitySearchType searchType,

        @Schema(description = "검색어. 앞뒤 공백을 지운 뒤 2~50자이고, 없거나 공백뿐이면 검색하지 않는다.")
        @Size(min = 2, max = 50, message = "검색어는 2자 이상 50자 이하여야 합니다.")
        String keyword,

        @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.") Integer page,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.") @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.")
        Integer size) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    // 검색어 길이는 앞뒤 공백을 지운 값으로 검사해야 한다. 그래서 검증보다 먼저 도는 생성자에서 공백을 지우고, 남는 글자가 없으면 검색하지 않는 것으로 본다.
    public CommunityPostListRequest {
        keyword = keyword == null || keyword.isBlank() ? null : keyword.strip();
    }

    CommunityPostListQuery toQuery() {
        return new CommunityPostListQuery(spotId, Boolean.TRUE.equals(popular), searchType, keyword);
    }

    int pageOrDefault() {
        return page == null ? DEFAULT_PAGE : page;
    }

    int sizeOrDefault() {
        return size == null ? DEFAULT_SIZE : size;
    }
}
