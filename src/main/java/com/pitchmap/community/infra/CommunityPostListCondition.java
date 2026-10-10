package com.pitchmap.community.infra;

/**
 * 글 목록과 개수 세기가 같이 쓰는 조건. 값이 null이면 그 조건으로 거르지 않는다.
 *
 * @param spotId 연결한 장소
 * @param minLikeCount 좋아요가 이 수 이상인 글만 남긴다
 * @param searchType 검색 대상 열거값 이름(TITLE_CONTENT, TITLE, CONTENT, AUTHOR). keyword가 있을 때만 쓴다.
 * @param keyword LIKE 패턴 안에 넣을 검색어. 호출하는 쪽이 {@code \}, {@code %}, {@code _} 앞에 {@code \}를 붙여 둔다.
 */
public record CommunityPostListCondition(Long spotId, Integer minLikeCount, String searchType, String keyword) {}
