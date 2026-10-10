package com.pitchmap.admin.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pitchmap.community.application.AdminCommunityAuthor;
import com.pitchmap.community.application.AdminCommunityReasonCounts;
import com.pitchmap.community.application.AdminCommunityRecentReport;
import java.time.Instant;
import java.util.List;

/** 관리자 글·댓글 검토 목록 응답이 함께 쓰는 작은 값들. 신고 수와 최근 신고는 검토 전 신고만 담고 신고자는 담지 않는다. */
public final class AdminCommunityReports {

    private AdminCommunityReports() {}

    public record Author(long memberId, String nickname) {

        static Author from(AdminCommunityAuthor author) {
            return new Author(author.memberId(), author.nickname());
        }
    }

    public record ReasonCounts(
            @JsonProperty("SPAM") long spam,
            @JsonProperty("ABUSE") long abuse,
            @JsonProperty("ILLEGAL_CAMPING") long illegalCamping,
            @JsonProperty("PRIVACY") long privacy,
            @JsonProperty("MONEY_SCAM") long moneyScam,
            @JsonProperty("OTHER") long other) {

        static ReasonCounts from(AdminCommunityReasonCounts counts) {
            return new ReasonCounts(
                    counts.spam(),
                    counts.abuse(),
                    counts.illegalCamping(),
                    counts.privacy(),
                    counts.moneyScam(),
                    counts.other());
        }
    }

    public record RecentReport(String reason, String content, Instant createdAt) {

        static List<RecentReport> fromAll(List<AdminCommunityRecentReport> reports) {
            return reports.stream()
                    .map(report -> new RecentReport(report.reason(), report.content(), report.createdAt()))
                    .toList();
        }
    }
}
