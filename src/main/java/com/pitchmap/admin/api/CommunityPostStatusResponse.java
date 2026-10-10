package com.pitchmap.admin.api;

import com.pitchmap.community.application.CommunityModerationResult;

/** 글 숨김·복구의 응답. status는 처리 뒤의 글 상태다. */
public record CommunityPostStatusResponse(long postId, String status) {

    static CommunityPostStatusResponse from(CommunityModerationResult result) {
        return new CommunityPostStatusResponse(result.id(), result.status());
    }
}
