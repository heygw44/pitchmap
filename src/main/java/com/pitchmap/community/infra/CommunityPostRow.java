package com.pitchmap.community.infra;

import com.pitchmap.community.domain.CommunityCategory;
import java.time.Instant;

/**
 * 글 조회가 읽은 글 한 건과 작성자의 현재 닉네임, 보이는 연결 장소. MyBatis가 이름으로 매핑하므로, 구성요소 이름은 열 별칭을 camelCase로 바꾼 것과 같아야 한다.
 *
 * <p>text는 목록에서는 본문 앞 100자이고, 상세에서는 본문 전체다. spotId와 spotName은 연결한 장소가 없거나 ACTIVE가 아니면 null이다. commentCount는 ACTIVE인 댓글과 답글의 수다. likeCount는 좋아요 수다.
 * thumbnailKey는 글 안 순서가 가장 앞선 이미지의 저장소 객체 키로, 이미지가 없으면 null이고 응답에는 내보내지 않는다. imageCount는 글에 붙은 이미지 수다.
 * likedByMe는 조회한 회원이 좋아요를 눌렀는지이고, 조회하는 회원을 넘기지 않으면 null이다.
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
        String thumbnailKey,
        long imageCount,
        long likeCount,
        long commentCount,
        Instant createdAt,
        Instant updatedAt,
        Boolean likedByMe) {}
