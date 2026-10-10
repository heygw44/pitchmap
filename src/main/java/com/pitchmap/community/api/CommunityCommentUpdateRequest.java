package com.pitchmap.community.api;

import com.pitchmap.community.domain.CommunityComment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommunityCommentUpdateRequest(
        @NotBlank(message = "댓글을 입력해야 합니다.")
        @Size(max = CommunityComment.CONTENT_MAX_LENGTH, message = "댓글은 1000자 이하여야 합니다.")
        String content) {}
