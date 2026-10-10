package com.pitchmap.community.api;

import com.pitchmap.community.application.CommunityCommentWriteCommand;
import com.pitchmap.community.domain.CommunityComment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// parentId인 댓글이 같은 글의 ACTIVE 최상위 댓글인지는 댓글 행을 읽어야 해서 서비스가 검사한다.
public record CommunityCommentCreateRequest(
        @NotBlank(message = "댓글을 입력해야 합니다.")
        @Size(max = CommunityComment.CONTENT_MAX_LENGTH, message = "댓글은 1000자 이하여야 합니다.")
        String content,

        @Schema(description = "답글을 달 댓글의 ID. 최상위 댓글이면 보내지 않는다.")
        Long parentId) {

    CommunityCommentWriteCommand toCommand() {
        return new CommunityCommentWriteCommand(content, parentId);
    }
}
