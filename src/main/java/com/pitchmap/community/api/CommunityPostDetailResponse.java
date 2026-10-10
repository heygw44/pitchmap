package com.pitchmap.community.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pitchmap.community.api.CommunityPostItemResponse.Author;
import com.pitchmap.community.api.CommunityPostItemResponse.LinkedSpot;
import com.pitchmap.community.application.CommunityPostItem;
import com.pitchmap.community.domain.CommunityCategory;
import java.time.Instant;

// 목록 항목과 같은 공개 범위를 따른다. 목록의 excerpt 대신 본문 전체와 수정 시각을 준다.
public record CommunityPostDetailResponse(
        long postId,
        CommunityCategory category,
        String title,
        String content,
        Author author,
        @JsonInclude(JsonInclude.Include.NON_NULL) LinkedSpot spot,
        Instant createdAt,
        Instant updatedAt) {

    static CommunityPostDetailResponse from(CommunityPostItem item) {
        return new CommunityPostDetailResponse(
                item.postId(),
                item.category(),
                item.title(),
                item.text(),
                new Author(item.authorId(), item.authorNickname()),
                LinkedSpot.of(item),
                item.createdAt(),
                item.updatedAt());
    }
}
