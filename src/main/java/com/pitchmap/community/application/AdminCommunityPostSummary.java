package com.pitchmap.community.application;

import java.time.Instant;
import java.util.List;

/**
 * 관리자 글 검토 목록의 한 항목. status는 열거형 이름이다.
 *
 * <p>content는 숨긴 글도 원문 전체를 담는다. reportCount, reasonCounts, recentReports는 검토 전 신고만 담고 신고자는 담지 않는다.
 * statusChangedAt은 글이 마지막으로 바뀐 시각이다.
 */
public record AdminCommunityPostSummary(
        long postId,
        String title,
        String content,
        AdminCommunityAuthor author,
        String status,
        long reportCount,
        AdminCommunityReasonCounts reasonCounts,
        List<AdminCommunityRecentReport> recentReports,
        Instant statusChangedAt) {}
