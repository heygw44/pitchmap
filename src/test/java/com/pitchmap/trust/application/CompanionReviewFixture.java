package com.pitchmap.trust.application;

import static com.pitchmap.member.domain.MemberBuilder.aMember;

import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.infra.MemberJpaRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;

// 동행 후기 통합 테스트들이 함께 쓰는 JDBC 데이터 준비 도구다. trust 코드는 basecamp 클래스를 쓰지 않으므로,
// 테스트도 베이스캠프와 멤버 행을 SQL로 직접 넣어 원하는 상태에서 시작한다.
final class CompanionReviewFixture {

    private static final String AT = "2026-09-01 03:00:00";

    private final JdbcTemplate jdbc;
    private final MemberJpaRepository memberRepository;

    CompanionReviewFixture(JdbcTemplate jdbc, MemberJpaRepository memberRepository) {
        this.jdbc = jdbc;
        this.memberRepository = memberRepository;
    }

    /** 본인확인을 마치지 않은 회원을 저장한다. 신뢰 단계가 0이다. */
    long saveMember(String nickname) {
        return memberRepository
                .saveAndFlush(aMember().nickname(nickname).build())
                .getId();
    }

    /** 본인확인을 마친 2007년생 성인 회원을 저장한다. 신뢰 단계가 1이다. */
    long saveVerifiedMember(String nickname) {
        long memberId = saveMember(nickname);
        jdbc.update(
                "INSERT INTO identity_verification (member_id, birth_year, gender, ci_hash, provider, verified_at,"
                        + " created_at, updated_at) VALUES (?, 2007, 'MALE', ?, 'FAKE', ?, ?, ?)",
                memberId,
                "%064d".formatted(memberId),
                AT,
                AT,
                AT);
        return memberId;
    }

    /** 상태가 status이고 completedAt에 완료된 베이스캠프를 저장하고 ID를 돌려준다. 멤버는 따로 넣는다. */
    long saveBasecamp(String status, Instant completedAt) {
        long leaderId = saveVerifiedMember(TestSequence.nickname());
        jdbc.update("INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                + " VALUES ('BAKJI', '개머리언덕', ST_GeomFromText('POINT(37.25 127.25)', 4326), 60, 127, 'ACTIVE',"
                + " NOW(6), NOW(6))");
        long spotId = jdbc.queryForObject("SELECT MAX(id) FROM spot", Long.class);
        jdbc.update(
                "INSERT INTO basecamp (leader_id, spot_id, title, description, start_date, end_date, capacity, status,"
                        + " completed_at, created_at, updated_at)"
                        + " VALUES (?, ?, '굴업도 주말 1박', '함께 가요', '2026-09-20', '2026-09-22', 4, ?, ?, ?, ?)",
                leaderId,
                spotId,
                status,
                completedAt == null ? null : LocalDateTime.ofInstant(completedAt, ZoneOffset.UTC),
                AT,
                AT);
        long basecampId = jdbc.queryForObject("SELECT MAX(id) FROM basecamp", Long.class);
        insertMember(basecampId, leaderId, "LEADER", "ACTIVE");
        return basecampId;
    }

    long leaderIdOf(long basecampId) {
        return jdbc.queryForObject("SELECT leader_id FROM basecamp WHERE id = ?", Long.class, basecampId);
    }

    void insertMember(long basecampId, long memberId, String role, String status) {
        jdbc.update(
                "INSERT INTO basecamp_member (basecamp_id, member_id, role, status, joined_at, left_at, created_at,"
                        + " updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                basecampId,
                memberId,
                role,
                status,
                AT,
                "ACTIVE".equals(status) ? null : AT,
                AT,
                AT);
    }

    /** 숨기지 않은 후기를 JDBC로 직접 넣는다. 서비스를 거치지 않아 자격과 기한을 검사하지 않는다. */
    long insertReview(long basecampId, long reviewerId, long revieweeId, boolean rejoinWanted, Instant createdAt) {
        jdbc.update(
                "INSERT INTO companion_review (basecamp_id, reviewer_id, reviewee_id, rejoin_wanted, comment,"
                        + " created_at) VALUES (?, ?, ?, ?, ?, ?)",
                basecampId,
                reviewerId,
                revieweeId,
                rejoinWanted,
                "코멘트 " + reviewerId + "→" + revieweeId,
                LocalDateTime.ofInstant(createdAt, ZoneOffset.UTC));
        return jdbc.queryForObject("SELECT MAX(id) FROM companion_review", Long.class);
    }

    void insertTag(long reviewId, String tag) {
        jdbc.update("INSERT INTO companion_review_tag (review_id, tag) VALUES (?, ?)", reviewId, tag);
    }

    void hide(long reviewId) {
        jdbc.update("UPDATE companion_review SET hidden_at = ? WHERE id = ?", AT, reviewId);
    }

    int reviewCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM companion_review", Integer.class);
    }
}
