package com.pitchmap.community.application;

import com.pitchmap.community.domain.CommunityCategory;

/** 글 작성 요청. spotId는 장소를 연결하지 않으면 null이다. */
public record CommunityPostWriteCommand(CommunityCategory category, String title, String content, Long spotId) {}
