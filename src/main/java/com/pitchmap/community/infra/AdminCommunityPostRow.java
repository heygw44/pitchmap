package com.pitchmap.community.infra;

import java.time.Instant;

/**
 * 관리자 글 검토 목록의 한 행. MyBatis가 이름으로 매핑하므로, 구성요소 이름은 열 별칭을 camelCase로 바꾼 것과 같아야 한다.
 *
 * <p>신고 수 일곱 개는 검토 전 신고만 센 값이다. content는 숨긴 글도 원문 그대로 담는다.
 */
public record AdminCommunityPostRow(
        long postId,
        String category,
        String title,
        String content,
        long authorId,
        String authorNickname,
        String status,
        long reportCount,
        long spamCount,
        long abuseCount,
        long illegalCampingCount,
        long privacyCount,
        long moneyScamCount,
        long otherCount,
        Instant statusChangedAt) {}
