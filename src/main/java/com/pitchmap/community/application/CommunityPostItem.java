package com.pitchmap.community.application;

import com.pitchmap.community.infra.CommunityPostRow;
import java.time.Instant;
import java.util.List;

/**
 * 글 한 건과 작성자. authorNickname은 조회한 시점의 닉네임이라서, 탈퇴한 회원이면 익명 닉네임이다.
 *
 * @param text 목록에서는 본문 앞 100자, 상세에서는 본문 전체
 * @param spotId 연결한 장소가 없거나 ACTIVE가 아니면 null
 * @param spotName spotId가 null이면 null
 * @param thumbnailUrl 목록에서 글 안 순서가 가장 앞선 이미지의 조회 URL. 이미지가 없거나 상세이면 null
 * @param imageCount 글에 붙은 이미지 수
 * @param images 상세에서 글 안 순서대로 담은 이미지. 목록이거나 이미지가 없으면 빈 목록
 * @param likeCount 좋아요 수
 * @param commentCount ACTIVE인 댓글과 답글의 수
 * @param likedByMe 조회한 회원이 좋아요를 눌렀는지. 조회하는 회원이 없으면 null
 */
public record CommunityPostItem(
        long postId,
        String title,
        String text,
        long authorId,
        String authorNickname,
        Long spotId,
        String spotName,
        String thumbnailUrl,
        long imageCount,
        List<CommunityPostImage> images,
        long likeCount,
        long commentCount,
        Instant createdAt,
        Instant updatedAt,
        Boolean likedByMe) {

    static CommunityPostItem from(CommunityPostRow row, String thumbnailUrl, List<CommunityPostImage> images) {
        return new CommunityPostItem(
                row.postId(),
                row.title(),
                row.text(),
                row.authorId(),
                row.authorNickname(),
                row.spotId(),
                row.spotName(),
                thumbnailUrl,
                row.imageCount(),
                images,
                row.likeCount(),
                row.commentCount(),
                row.createdAt(),
                row.updatedAt(),
                row.likedByMe());
    }
}
