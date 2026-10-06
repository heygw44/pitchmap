package com.pitchmap.review.application;

/** 후기 수정 요청. 평점과 내용을 둘 다 담는다. 방문일은 고칠 수 없어서 담지 않는다. */
public record SpotReviewReviseCommand(int rating, String content) {}
