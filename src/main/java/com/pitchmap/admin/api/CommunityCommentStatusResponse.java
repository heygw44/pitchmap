package com.pitchmap.admin.api;

import com.pitchmap.community.application.CommunityModerationResult;

/** 댓글 숨김·복구의 응답. status는 처리 뒤의 댓글 상태다. */
public record CommunityCommentStatusResponse(long commentId, String status) {

    static CommunityCommentStatusResponse from(CommunityModerationResult result) {
        return new CommunityCommentStatusResponse(result.id(), result.status());
    }
}
