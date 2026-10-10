package com.pitchmap.community.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pitchmap.community.application.CommunityCommentItem;
import java.time.Instant;
import java.util.List;

// 작성자는 회원 ID와 닉네임만 내보낸다. 이메일 같은 다른 회원 정보는 응답에 넣지 않는다.
// 삭제·숨김·검토 대기 댓글은 답글 때문에 자리만 남으므로, 작성자·내용·시각 필드를 null이 아니라 아예 뺀다.
public record CommunityCommentItemResponse(
        long commentId,
        @JsonInclude(JsonInclude.Include.NON_NULL) Author author,
        @JsonInclude(JsonInclude.Include.NON_NULL) String content,
        boolean deleted,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant createdAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant updatedAt,
        List<CommunityCommentItemResponse> replies) {

    static CommunityCommentItemResponse from(CommunityCommentItem item) {
        return new CommunityCommentItemResponse(
                item.commentId(),
                item.deleted() ? null : new Author(item.authorId(), item.authorNickname()),
                item.content(),
                item.deleted(),
                item.createdAt(),
                item.updatedAt(),
                item.replies().stream().map(CommunityCommentItemResponse::from).toList());
    }

    public record Author(long memberId, String nickname) {}
}
