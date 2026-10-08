package com.pitchmap.trust.application;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;

// 관리자 신고 처리 통합 테스트들이 함께 쓰는 JDBC 데이터 준비 도구다. 신고 접수 자격 검사를 거치지 않고 원하는 상태의 신고 행을 바로 넣는다.
public final class MemberReportFixture {

    private final JdbcTemplate jdbc;

    public MemberReportFixture(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 회원 신고(kind=MEMBER)를 status 상태로 넣고 신고 ID를 돌려준다. 긴급 여부는 유형에서 정한다. */
    public long insertMemberReport(
            long reporterId, long targetId, long basecampId, String type, String status, Instant createdAt) {
        return insert(reporterId, targetId, basecampId, "MEMBER", null, type, status, createdAt);
    }

    /** 후기 신고(kind=REVIEW)를 status 상태로 넣고 신고 ID를 돌려준다. */
    public long insertReviewReport(
            long reporterId, long targetId, long basecampId, long reviewId, String status, Instant createdAt) {
        return insert(reporterId, targetId, basecampId, "REVIEW", reviewId, "INAPPROPRIATE_REVIEW", status, createdAt);
    }

    private long insert(
            long reporterId,
            long targetId,
            long basecampId,
            String kind,
            Long reviewId,
            String type,
            String status,
            Instant createdAt) {
        LocalDateTime at = LocalDateTime.ofInstant(createdAt, ZoneOffset.UTC);
        jdbc.update(
                "INSERT INTO member_report (reporter_id, target_member_id, basecamp_id, kind, companion_review_id,"
                        + " type, content, urgent, status, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                reporterId,
                targetId,
                basecampId,
                kind,
                reviewId,
                type,
                "신고 내용 " + type,
                "HARASSMENT_OR_THREAT".equals(type),
                status,
                at,
                at);
        return jdbc.queryForObject("SELECT MAX(id) FROM member_report", Long.class);
    }
}
