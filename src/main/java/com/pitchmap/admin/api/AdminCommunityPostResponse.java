package com.pitchmap.admin.api;

import com.pitchmap.community.application.AdminCommunityPostSummary;
import java.time.Instant;
import java.util.List;

/** 관리자 글 검토 목록의 한 항목. 숨긴 글도 content에 원문 전체를 담는다. */
public record AdminCommunityPostResponse(
        long postId,
        String title,
        String content,
        AdminCommunityReports.Author author,
        String status,
        long reportCount,
        AdminCommunityReports.ReasonCounts reasonCounts,
        List<AdminCommunityReports.RecentReport> recentReports,
        Instant statusChangedAt) {

    static AdminCommunityPostResponse from(AdminCommunityPostSummary summary) {
        return new AdminCommunityPostResponse(
                summary.postId(),
                summary.title(),
                summary.content(),
                AdminCommunityReports.Author.from(summary.author()),
                summary.status(),
                summary.reportCount(),
                AdminCommunityReports.ReasonCounts.from(summary.reasonCounts()),
                AdminCommunityReports.RecentReport.fromAll(summary.recentReports()),
                summary.statusChangedAt());
    }
}
