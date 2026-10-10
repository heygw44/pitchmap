package com.pitchmap.community.api;

import com.pitchmap.common.web.PatchField;
import com.pitchmap.community.application.CommunityPostReviseCommand;
import com.pitchmap.community.domain.CommunityPost;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 글 수정 요청이다. 모든 필드가 선택이고, 필드를 빼는 것과 {@code null}을 보내는 것의 뜻이 다르다.
 * 서버는 요청에 없는 필드를 그대로 두고, {@code spotId}를 {@code null}로 보내면 장소 연결을 끊는다.
 * 제목과 본문은 비울 수 없어서 서버가 {@code null}을 거부한다.
 *
 * <p>제목·본문의 길이와 장소 상태는 서비스가 검증한다. 이 요청은 받은 값을 그대로 넘긴다.
 *
 * <p>JSON에 빠진 필드를 Jackson이 Java {@code null}로 넘기는 경우가 있다. 그래서 생성자가 {@code null}을 "요청에 없음"으로 바꾼다.
 */
public record CommunityPostUpdateRequest(
        @Schema(
                implementation = String.class,
                maxLength = CommunityPost.TITLE_MAX_LENGTH,
                description = "공백뿐인 값은 쓸 수 없다. null로 보내면 400이다.")
        PatchField<String> title,

        @Schema(
                implementation = String.class,
                maxLength = CommunityPost.CONTENT_MAX_LENGTH,
                description = "공백뿐인 값은 쓸 수 없다. null로 보내면 400이다.")
        PatchField<String> content,

        @Schema(implementation = Long.class, description = "연결할 장소 ID. null로 보내면 장소 연결을 끊는다.")
        PatchField<Long> spotId,

        @Schema(
                implementation = Long[].class,
                description = "붙일 이미지 ID. 보내면 글의 이미지를 배열 순서대로 통째로 바꾸고, 빈 배열은 이미지를 모두 뗀다. null로 보내면 400이다.")
        PatchField<List<Long>> imageIds) {

    public CommunityPostUpdateRequest {
        title = absentIfNull(title);
        content = absentIfNull(content);
        spotId = absentIfNull(spotId);
        imageIds = absentIfNull(imageIds);
    }

    CommunityPostReviseCommand toCommand() {
        return new CommunityPostReviseCommand(title, content, spotId, imageIds);
    }

    private static <T> PatchField<T> absentIfNull(PatchField<T> field) {
        return field == null ? PatchField.absent() : field;
    }
}
