package com.pitchmap.community.application;

/**
 * 글 목록 조건.
 *
 * @param spotId 연결한 장소로 거른다. null이면 거르지 않는다.
 * @param popular true면 좋아요를 {@link com.pitchmap.community.domain.CommunityPost#POPULAR_LIKE_THRESHOLD}개 이상 받은 글만 준다
 */
public record CommunityPostListQuery(Long spotId, boolean popular) {

    /** 조건 없이 모든 글을 읽는 목록이다. */
    public static CommunityPostListQuery all() {
        return new CommunityPostListQuery(null, false);
    }
}
