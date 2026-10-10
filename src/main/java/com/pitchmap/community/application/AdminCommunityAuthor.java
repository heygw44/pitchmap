package com.pitchmap.community.application;

/** 관리자 검토 목록의 작성자. nickname은 조회한 시점의 닉네임이다. */
public record AdminCommunityAuthor(long memberId, String nickname) {}
