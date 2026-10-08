package com.pitchmap.spot.infra;

import java.time.Instant;

/** 관리자 박지 검토 목록에 붙일 검토 전 신고 한 건. 신고자는 읽지 않는다. */
public record AdminSpotRecentReportRow(long spotId, String reason, String content, Instant createdAt) {}
