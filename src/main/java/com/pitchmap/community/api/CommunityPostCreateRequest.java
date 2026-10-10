package com.pitchmap.community.api;

import com.pitchmap.community.application.CommunityPostWriteCommand;
import com.pitchmap.community.domain.CommunityCategory;
import com.pitchmap.community.domain.CommunityPost;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

// 연결할 장소가 ACTIVE인지는 장소 상태를 읽어야 해서 서비스가 검사한다. 이미지도 개수, 소유자, 저장소 객체를 서비스가 검사한다.
public record CommunityPostCreateRequest(
        @NotNull(message = "카테고리를 입력해야 합니다.") CommunityCategory category,

        @NotBlank(message = "제목을 입력해야 합니다.") @Size(max = CommunityPost.TITLE_MAX_LENGTH, message = "제목은 100자 이하여야 합니다.")
        String title,

        @NotBlank(message = "본문을 입력해야 합니다.")
        @Size(max = CommunityPost.CONTENT_MAX_LENGTH, message = "본문은 10000자 이하여야 합니다.")
        String content,

        @Schema(description = "연결할 장소 ID. 연결하지 않으면 보내지 않는다.")
        Long spotId,

        @Schema(description = "붙일 이미지 ID. 최대 5개이고 배열 순서가 글 안 순서다. 붙이지 않으면 보내지 않는다.")
        List<Long> imageIds) {

    CommunityPostWriteCommand toCommand() {
        return new CommunityPostWriteCommand(category, title, content, spotId, imageIds);
    }
}
