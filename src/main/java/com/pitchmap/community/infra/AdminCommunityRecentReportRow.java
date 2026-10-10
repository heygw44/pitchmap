package com.pitchmap.community.infra;

import java.time.Instant;

/** 관리자 커뮤니티 검토 목록에 붙일 검토 전 신고 한 건. 신고자는 읽지 않는다. */
public record AdminCommunityRecentReportRow(long targetId, String reason, String content, Instant createdAt) {}
