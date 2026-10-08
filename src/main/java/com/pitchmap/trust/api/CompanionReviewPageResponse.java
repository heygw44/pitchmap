package com.pitchmap.trust.api;

import com.pitchmap.trust.application.CompanionReviewPage;
import java.util.List;
import java.util.function.Function;

/** 동행 후기 목록의 한 페이지 응답이다. 전체 개수는 없고 다음 페이지가 있는지만 hasNext로 알려 준다. */
public record CompanionReviewPageResponse<T>(List<T> content, int page, int size, boolean hasNext) {

    static <S, T> CompanionReviewPageResponse<T> from(CompanionReviewPage<S> page, Function<S, T> mapper) {
        return new CompanionReviewPageResponse<>(
                page.content().stream().map(mapper).toList(), page.page(), page.size(), page.hasNext());
    }
}
