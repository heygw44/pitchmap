package com.pitchmap.community.application;

/** 댓글 작성 요청. parentId는 최상위 댓글이면 null이고, 답글이면 부모 댓글의 ID다. */
public record CommunityCommentWriteCommand(String content, Long parentId) {}
