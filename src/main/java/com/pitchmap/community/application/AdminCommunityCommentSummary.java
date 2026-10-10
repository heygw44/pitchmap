package com.pitchmap.community.application;

import java.time.Instant;
import java.util.List;

/**
 * 관리자 댓글 검토 목록의 한 항목. status는 열거형 이름이다.
 *
 * <p>content는 숨긴 댓글도 원문 전체를 담고, 작성자가 지운 댓글이면 {@code null}이다. reportCount, reasonCounts, recentReports는 검토 전
 * 신고만 담고 신고자는 담지 않는다. statusChangedAt은 댓글이 마지막으로 바뀐 시각이다.
 */
public record AdminCommunityCommentSummary(
        long commentId,
        long postId,
        String content,
        AdminCommunityAuthor author,
        String status,
        long reportCount,
        AdminCommunityReasonCounts reasonCounts,
        List<AdminCommunityRecentReport> recentReports,
        Instant statusChangedAt) {}
