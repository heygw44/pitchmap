package com.pitchmap.community.application;

import com.pitchmap.community.domain.CommunityCategory;
import com.pitchmap.community.infra.CommunityPostRow;
import java.time.Instant;

/**
 * 글 한 건과 작성자. authorNickname은 조회한 시점의 닉네임이라서, 탈퇴한 회원이면 익명 닉네임이다.
 *
 * @param text 목록에서는 본문 앞 100자, 상세에서는 본문 전체
 * @param spotId 연결한 장소가 없거나 ACTIVE가 아니면 null
 * @param spotName spotId가 null이면 null
 */
public record CommunityPostItem(
        long postId,
        CommunityCategory category,
        String title,
        String text,
        long authorId,
        String authorNickname,
        Long spotId,
        String spotName,
        Instant createdAt,
        Instant updatedAt) {

    static CommunityPostItem from(CommunityPostRow row) {
        return new CommunityPostItem(
                row.postId(),
                row.category(),
                row.title(),
                row.text(),
                row.authorId(),
                row.authorNickname(),
                row.spotId(),
                row.spotName(),
                row.createdAt(),
                row.updatedAt());
    }
}
