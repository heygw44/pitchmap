package com.pitchmap.community.application;

/** 글이나 댓글을 숨기거나 복구한 결과. id는 처리한 글 또는 댓글의 ID이고, status는 처리 뒤의 상태 이름이다. */
public record CommunityModerationResult(long id, String status) {}
