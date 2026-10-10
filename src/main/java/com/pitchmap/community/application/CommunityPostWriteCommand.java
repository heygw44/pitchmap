package com.pitchmap.community.application;

import java.util.List;

/**
 * 글 작성 요청. spotId는 장소를 연결하지 않으면 null이다. imageIds는 붙일 이미지의 ID이고 순서가 글 안 순서이며, 붙이지 않으면 빈 목록이다.
 */
public record CommunityPostWriteCommand(String title, String content, Long spotId, List<Long> imageIds) {

    public CommunityPostWriteCommand {
        imageIds = imageIds == null ? List.of() : List.copyOf(imageIds);
    }

    /** 이미지를 붙이지 않는 글 작성 요청이다. */
    public CommunityPostWriteCommand(String title, String content, Long spotId) {
        this(title, content, spotId, List.of());
    }
}
