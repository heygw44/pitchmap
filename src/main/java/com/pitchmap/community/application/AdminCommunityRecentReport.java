package com.pitchmap.community.application;

import java.time.Instant;

/** 관리자 검토 목록에 붙는 검토 전 신고 한 건. reason은 열거형 이름이고, 신고자는 담지 않는다. */
public record AdminCommunityRecentReport(String reason, String content, Instant createdAt) {}
