package com.pitchmap.community.application;

/**
 * 글 목록 조건. 조건끼리는 모두 함께 쓸 수 있다.
 *
 * @param spotId 연결한 장소로 거른다. null이면 거르지 않는다.
 * @param popular true면 좋아요를 {@link com.pitchmap.community.domain.CommunityPost#POPULAR_LIKE_THRESHOLD}개 이상 받은 글만 준다
 * @param searchType 검색 대상. keyword가 null이면 쓰지 않고, keyword가 있는데 null이면 제목과 본문을 찾는다.
 * @param keyword 앞뒤 공백을 지운 검색어. null이면 검색하지 않는다.
 */
public record CommunityPostListQuery(Long spotId, boolean popular, CommunitySearchType searchType, String keyword) {

    public CommunityPostListQuery {
        if (keyword == null) {
            searchType = null;
        } else if (searchType == null) {
            searchType = CommunitySearchType.TITLE_CONTENT;
        }
    }

    /** 검색하지 않는 목록 조건이다. */
    public CommunityPostListQuery(Long spotId, boolean popular) {
        this(spotId, popular, null, null);
    }

    /** 조건 없이 모든 글을 읽는 목록이다. */
    public static CommunityPostListQuery all() {
        return new CommunityPostListQuery(null, false);
    }
}
