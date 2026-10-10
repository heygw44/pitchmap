package com.pitchmap.admin.api;

import com.pitchmap.community.application.AdminCommunityPage;
import java.util.List;
import java.util.function.Function;

/** 관리자 커뮤니티 검토 목록의 한 페이지 응답이다. 전체 개수는 없고 다음 페이지가 있는지만 hasNext로 알려 준다. */
public record AdminCommunityPageResponse<T>(List<T> content, int page, int size, boolean hasNext) {

    static <S, T> AdminCommunityPageResponse<T> from(AdminCommunityPage<S> page, Function<S, T> mapper) {
        return new AdminCommunityPageResponse<>(
                page.content().stream().map(mapper).toList(), page.page(), page.size(), page.hasNext());
    }
}
