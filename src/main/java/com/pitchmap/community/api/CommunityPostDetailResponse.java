package com.pitchmap.community.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pitchmap.community.api.CommunityPostItemResponse.Author;
import com.pitchmap.community.api.CommunityPostItemResponse.LinkedSpot;
import com.pitchmap.community.application.CommunityPostItem;
import java.time.Instant;
import java.util.List;

// 목록 항목과 같은 공개 범위를 따른다. 목록의 excerpt 대신 본문 전체와 수정 시각을 준다.
// likedByMe는 로그인한 회원에게만 주고, 비회원에게는 null이 아니라 필드를 뺀다.
public record CommunityPostDetailResponse(
        long postId,
        String title,
        String content,
        List<Image> images,
        Author author,
        @JsonInclude(JsonInclude.Include.NON_NULL) LinkedSpot spot,
        long likeCount,
        long commentCount,
        long viewCount,
        Instant createdAt,
        Instant updatedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean likedByMe) {

    /** 글 안 순서대로 담는다. 이미지가 없으면 빈 배열이다. url은 응답마다 새로 서명한 조회 URL이다. */
    public record Image(long imageId, String url) {}

    static CommunityPostDetailResponse from(CommunityPostItem item) {
        return new CommunityPostDetailResponse(
                item.postId(),
                item.title(),
                item.text(),
                item.images().stream()
                        .map(image -> new Image(image.imageId(), image.url()))
                        .toList(),
                new Author(item.authorId(), item.authorNickname()),
                LinkedSpot.of(item),
                item.likeCount(),
                item.commentCount(),
                item.viewCount(),
                item.createdAt(),
                item.updatedAt(),
                item.likedByMe());
    }
}
