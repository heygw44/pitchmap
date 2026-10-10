package com.pitchmap.community.application;

/**
 * 좋아요를 누르거나 취소한 뒤의 상태.
 *
 * @param liked 요청한 회원이 지금 그 글에 좋아요를 눌러 둔 상태인지
 * @param likeCount 그 글의 좋아요 수
 */
public record CommunityPostLikeResult(boolean liked, long likeCount) {}
