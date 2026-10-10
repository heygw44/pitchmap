package com.pitchmap.community.application;

import com.pitchmap.common.web.PatchField;
import com.pitchmap.community.domain.CommunityCategory;
import java.util.Objects;

/**
 * 글 수정 요청. 필드마다 요청에 있었는지와 값을 함께 담고, 요청에 없던 필드는 바꾸지 않는다.
 * 카테고리, 제목, 본문은 지울 수 없어서 서비스가 null을 거부한다. spotId를 null로 보냈으면 장소 연결을 끊는다.
 */
public record CommunityPostReviseCommand(
        PatchField<CommunityCategory> category,
        PatchField<String> title,
        PatchField<String> content,
        PatchField<Long> spotId) {

    public CommunityPostReviseCommand {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(spotId, "spotId");
    }
}
