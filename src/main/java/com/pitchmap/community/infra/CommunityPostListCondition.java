package com.pitchmap.community.infra;

/**
 * 글 목록과 개수 세기가 같이 쓰는 조건. 값이 null이면 그 조건으로 거르지 않는다.
 *
 * @param spotId 연결한 장소
 * @param minLikeCount 좋아요가 이 수 이상인 글만 남긴다
 */
public record CommunityPostListCondition(Long spotId, Integer minLikeCount) {}
