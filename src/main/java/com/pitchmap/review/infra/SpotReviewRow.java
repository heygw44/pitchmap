package com.pitchmap.review.infra;

import java.time.Instant;
import java.time.LocalDate;

/** 후기 조회가 읽은 후기 한 건과 작성자의 현재 닉네임. MyBatis가 이름으로 매핑하므로, 구성요소 이름은 열 별칭을 camelCase로 바꾼 것과 같아야 한다. */
public record SpotReviewRow(
        long reviewId,
        long authorId,
        String authorNickname,
        LocalDate visitedDate,
        int rating,
        String content,
        Instant createdAt) {}
