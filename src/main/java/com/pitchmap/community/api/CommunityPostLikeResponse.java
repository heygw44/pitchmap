package com.pitchmap.community.api;

import com.pitchmap.community.application.CommunityPostLikeResult;

public record CommunityPostLikeResponse(boolean liked, long likeCount) {

    static CommunityPostLikeResponse from(CommunityPostLikeResult result) {
        return new CommunityPostLikeResponse(result.liked(), result.likeCount());
    }
}
