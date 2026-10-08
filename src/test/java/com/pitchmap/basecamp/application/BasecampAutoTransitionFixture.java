package com.pitchmap.basecamp.application;

import static com.pitchmap.member.domain.MemberBuilder.aMember;

import com.pitchmap.member.infra.MemberJpaRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

// 자동 처리 통합 테스트들이 함께 쓰는 JDBC 데이터 준비와 조회 도구다. 서비스를 거치지 않고 행을 직접 넣어, 원하는 상태에서 시작한다.
final class BasecampAutoTransitionFixture {

    static final String AT_DEFAULT_INSTANT = "2026-10-05 03:00:00";
    static final LocalDate START_DATE = LocalDate.of(2026, 10, 20);
    static final LocalDate END_DATE = START_DATE.plusDays(2);

    private final JdbcTemplate jdbc;
    private final MemberJpaRepository memberRepository;
    private final JsonMapper jsonMapper;

    BasecampAutoTransitionFixture(JdbcTemplate jdbc, MemberJpaRepository memberRepository, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.memberRepository = memberRepository;
        this.jsonMapper = jsonMapper;
    }

    long saveMember() {
        return memberRepository.saveAndFlush(aMember().build()).getId();
    }

    // 본인확인을 마친 2007년생 성인 회원을 직접 저장한다. 신뢰 단계가 1이라 탈퇴할 자격이 있다.
    long saveVerifiedMember() {
        long memberId = saveMember();
        jdbc.update(
                "INSERT INTO identity_verification (member_id, birth_year, gender, ci_hash, provider, verified_at,"
                        + " created_at, updated_at) VALUES (?, 2007, 'MALE', ?, 'FAKE', ?, ?, ?)",
                memberId,
                "%064d".formatted(memberId),
                AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT);
        return memberId;
    }

    /** 캠프 리더만 ACTIVE 멤버인 베이스캠프를 저장하고 그 ID를 돌려준다. 캠프 리더의 ID는 {@link #leaderIdOf}로 읽는다. */
    long saveBasecamp(String status, String closedReason, LocalDate startDate, LocalDate endDate) {
        long leaderId = saveVerifiedMember();
        jdbc.update("INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                + " VALUES ('BAKJI', '개머리언덕', ST_GeomFromText('POINT(37.25 127.25)', 4326), 60, 127, 'ACTIVE',"
                + " NOW(6), NOW(6))");
        long spotId = jdbc.queryForObject("SELECT MAX(id) FROM spot", Long.class);
        jdbc.update(
                "INSERT INTO basecamp (leader_id, spot_id, title, description, start_date, end_date, capacity, status,"
                        + " closed_reason, created_at, updated_at)"
                        + " VALUES (?, ?, '굴업도 주말 1박', '함께 가요', ?, ?, 4, ?, ?, ?, ?)",
                leaderId,
                spotId,
                startDate,
                endDate,
                status,
                closedReason,
                AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT);
        long basecampId = jdbc.queryForObject("SELECT MAX(id) FROM basecamp", Long.class);
        insertMember(basecampId, leaderId, "LEADER", "ACTIVE");
        return basecampId;
    }

    long saveBasecamp(String status) {
        return saveBasecamp(status, null, START_DATE, END_DATE);
    }

    /** 캠프 리더 외에 ACTIVE 멤버가 memberCount명 더 있는 베이스캠프를 저장한다. */
    long saveBasecampWithMembers(String status, int memberCount) {
        long basecampId = saveBasecamp(status);
        for (int i = 0; i < memberCount; i++) {
            insertMember(basecampId, saveVerifiedMember(), "MEMBER", "ACTIVE");
        }
        return basecampId;
    }

    long leaderIdOf(long basecampId) {
        return jdbc.queryForObject("SELECT leader_id FROM basecamp WHERE id = ?", Long.class, basecampId);
    }

    long firstMemberIdOf(long basecampId) {
        return jdbc.queryForObject(
                "SELECT member_id FROM basecamp_member WHERE basecamp_id = ? AND role = 'MEMBER' ORDER BY id LIMIT 1",
                Long.class,
                basecampId);
    }

    void insertMember(long basecampId, long memberId, String role, String status) {
        jdbc.update(
                "INSERT INTO basecamp_member (basecamp_id, member_id, role, status, joined_at, left_at, created_at,"
                        + " updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                basecampId,
                memberId,
                role,
                status,
                AT_DEFAULT_INSTANT,
                "ACTIVE".equals(status) ? null : AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT);
    }

    long insertPendingApplication(long basecampId) {
        jdbc.update(
                "INSERT INTO basecamp_application (basecamp_id, applicant_id, status, created_at, updated_at)"
                        + " VALUES (?, ?, 'PENDING', ?, ?)",
                basecampId,
                saveVerifiedMember(),
                AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT);
        return jdbc.queryForObject("SELECT MAX(id) FROM basecamp_application", Long.class);
    }

    String statusOf(long basecampId) {
        return jdbc.queryForObject("SELECT status FROM basecamp WHERE id = ?", String.class, basecampId);
    }

    String applicationStatusOf(long applicationId) {
        return jdbc.queryForObject("SELECT status FROM basecamp_application WHERE id = ?", String.class, applicationId);
    }

    int eventCount(String eventType) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE event_type = ?", Integer.class, eventType);
    }

    int eventCount(String eventType, long basecampId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE event_type = ? AND aggregate_id = ?",
                Integer.class,
                eventType,
                basecampId);
    }

    int totalEventCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event", Integer.class);
    }

    int activeMemberCount(long basecampId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM basecamp_member WHERE basecamp_id = ? AND status = 'ACTIVE'",
                Integer.class,
                basecampId);
    }

    /** 이벤트가 하나뿐이라고 보고 그 payload의 회원 ID를 돌려준다. */
    List<Long> payloadMemberIdsOf(String eventType, long basecampId) {
        String json = jdbc.queryForObject(
                "SELECT payload FROM outbox_event WHERE event_type = ? AND aggregate_id = ?",
                String.class,
                eventType,
                basecampId);
        return jsonMapper
                .readValue(json, BasecampTransitionEvents.Payload.class)
                .memberIds();
    }
}
