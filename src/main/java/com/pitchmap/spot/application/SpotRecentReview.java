package com.pitchmap.spot.application;

import java.time.Instant;
import java.time.LocalDate;

/** 장소 상세에 싣는 최근 후기 한 건. authorNickname은 조회한 시점의 닉네임이라서, 탈퇴한 회원이면 익명 닉네임이다. */
public record SpotRecentReview(
        long reviewId,
        long authorId,
        String authorNickname,
        LocalDate visitedDate,
        int rating,
        String content,
        Instant createdAt) {}
