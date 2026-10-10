package com.pitchmap.community.api;

import com.pitchmap.community.application.CommunityPostPage;
import java.util.List;

// 다른 목록 응답과 달리 화면이 페이지 번호를 그리도록 전체 글 수와 페이지 수를 더 준다.
public record CommunityPostPageResponse(
        List<CommunityPostItemResponse> content,
        int page,
        int size,
        boolean hasNext,
        long totalElements,
        int totalPages) {

    static CommunityPostPageResponse from(CommunityPostPage page) {
        List<CommunityPostItemResponse> content =
                page.content().stream().map(CommunityPostItemResponse::from).toList();
        return new CommunityPostPageResponse(
                content, page.page(), page.size(), page.hasNext(), page.totalElements(), page.totalPages());
    }
}
