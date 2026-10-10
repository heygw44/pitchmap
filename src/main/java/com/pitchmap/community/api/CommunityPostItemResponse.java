package com.pitchmap.community.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pitchmap.community.application.CommunityPostItem;
import com.pitchmap.community.domain.CommunityCategory;
import java.time.Instant;

// 작성자는 회원 ID와 닉네임만 내보낸다. 이메일 같은 다른 회원 정보는 응답에 넣지 않는다.
// 연결한 장소가 없거나 지도에 보이지 않으면 spot 필드를 null이 아니라 아예 뺀다. 이미지가 없는 글도 thumbnailUrl 필드를 뺀다.
public record CommunityPostItemResponse(
        long postId,
        CommunityCategory category,
        String title,
        String excerpt,
        Author author,
        @JsonInclude(JsonInclude.Include.NON_NULL) LinkedSpot spot,
        @JsonInclude(JsonInclude.Include.NON_NULL) String thumbnailUrl,
        long imageCount,
        long likeCount,
        long commentCount,
        Instant createdAt) {

    static CommunityPostItemResponse from(CommunityPostItem item) {
        return new CommunityPostItemResponse(
                item.postId(),
                item.category(),
                item.title(),
                item.text(),
                new Author(item.authorId(), item.authorNickname()),
                LinkedSpot.of(item),
                item.thumbnailUrl(),
                item.imageCount(),
                item.likeCount(),
                item.commentCount(),
                item.createdAt());
    }

    public record Author(long memberId, String nickname) {}

    public record LinkedSpot(long spotId, String name) {

        static LinkedSpot of(CommunityPostItem item) {
            return item.spotId() == null ? null : new LinkedSpot(item.spotId(), item.spotName());
        }
    }
}
