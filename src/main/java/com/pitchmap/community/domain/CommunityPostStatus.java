package com.pitchmap.community.domain;

/** 커뮤니티 글의 상태. 목록과 상세에는 ACTIVE인 글만 나온다. */
public enum CommunityPostStatus {
    ACTIVE,
    /** 신고가 쌓여 검토를 기다리는 글 */
    PENDING_REVIEW,
    /** 관리자가 숨긴 글 */
    HIDDEN,
    /** 작성자가 지운 글. 행은 남긴다. */
    DELETED
}
