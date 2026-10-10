package com.pitchmap.community.infra;

import com.pitchmap.community.domain.CommunityCategory;
import java.time.Instant;

/**
 * 글 조회가 읽은 글 한 건과 작성자의 현재 닉네임, 보이는 연결 장소. MyBatis가 이름으로 매핑하므로, 구성요소 이름은 열 별칭을 camelCase로 바꾼 것과 같아야 한다.
 *
 * <p>text는 목록에서는 본문 앞 100자이고, 상세에서는 본문 전체다. spotId와 spotName은 연결한 장소가 없거나 ACTIVE가 아니면 null이다. commentCount는 ACTIVE인 댓글과 답글의 수다.
 */
public record CommunityPostRow(
        long postId,
        CommunityCategory category,
        String title,
        String text,
        long authorId,
        String authorNickname,
        Long spotId,
        String spotName,
        long commentCount,
        Instant createdAt,
        Instant updatedAt) {}
