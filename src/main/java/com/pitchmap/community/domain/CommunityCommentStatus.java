package com.pitchmap.community.domain;

/** 커뮤니티 댓글의 상태. 내용은 ACTIVE인 댓글만 보인다. */
public enum CommunityCommentStatus {
    ACTIVE,
    /** 신고가 쌓여 검토를 기다리는 댓글 */
    PENDING_REVIEW,
    /** 관리자가 숨긴 댓글 */
    HIDDEN,
    /** 작성자가 지운 댓글. 행은 남기고 내용은 지운다. */
    DELETED
}
