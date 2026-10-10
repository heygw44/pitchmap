package com.pitchmap.community.application;

/** 글 상세에 담는 이미지 한 장. url은 응답마다 새로 발급하는 조회용 사전 서명 URL이다. */
public record CommunityPostImage(long imageId, String url) {}
