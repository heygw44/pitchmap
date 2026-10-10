package com.pitchmap.admin.api;

import com.pitchmap.community.application.AdminCommunityCommentSummary;
import java.time.Instant;
import java.util.List;

/** 관리자 댓글 검토 목록의 한 항목. 숨긴 댓글도 content에 원문 전체를 담고, 작성자가 지운 댓글이면 content는 null이다. */
public record AdminCommunityCommentResponse(
        long commentId,
        long postId,
        String content,
        AdminCommunityReports.Author author,
        String status,
        long reportCount,
        AdminCommunityReports.ReasonCounts reasonCounts,
        List<AdminCommunityReports.RecentReport> recentReports,
        Instant statusChangedAt) {

    static AdminCommunityCommentResponse from(AdminCommunityCommentSummary summary) {
        return new AdminCommunityCommentResponse(
                summary.commentId(),
                summary.postId(),
                summary.content(),
                AdminCommunityReports.Author.from(summary.author()),
                summary.status(),
                summary.reportCount(),
                AdminCommunityReports.ReasonCounts.from(summary.reasonCounts()),
                AdminCommunityReports.RecentReport.fromAll(summary.recentReports()),
                summary.statusChangedAt());
    }
}
