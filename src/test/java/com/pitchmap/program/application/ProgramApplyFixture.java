package com.pitchmap.program.application;

import static com.pitchmap.member.domain.MemberBuilder.aMember;

import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.infra.MemberJpaRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;

// 선착순 신청 통합 테스트들이 함께 쓰는 JDBC 데이터 준비와 조회 도구다. 서비스를 거치지 않고 행을 직접 넣어, 원하는 상태에서 시작한다.
public final class ProgramApplyFixture {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;
    private static final String AT_DEFAULT_INSTANT = "2026-10-05 03:00:00";

    private final JdbcTemplate jdbc;
    private final MemberJpaRepository memberRepository;

    public ProgramApplyFixture(JdbcTemplate jdbc, MemberJpaRepository memberRepository) {
        this.jdbc = jdbc;
        this.memberRepository = memberRepository;
    }

    public long saveMember() {
        return memberRepository.saveAndFlush(aMember().build()).getId();
    }

    // 본인확인을 마친 2007년생 성인 회원을 직접 저장한다. 신뢰 단계가 1이다.
    public long saveVerifiedMember() {
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

    /** 지금 신청을 받는 중인 행사를 저장한다. 신청은 현재 시각 1시간 전에 열려 5일 뒤에 닫히고, 결제 기한은 15분이다. */
    public long saveProgram(int capacity, boolean overnight) {
        return saveProgram(
                capacity, overnight, NOW.minus(Duration.ofHours(1)), NOW.plus(Duration.ofDays(5)), "SCHEDULED");
    }

    public long saveProgram(int capacity, boolean overnight, Instant applyOpenAt, Instant applyCloseAt, String status) {
        long adminId = saveMember();
        Instant startAt = NOW.plus(Duration.ofDays(7));
        jdbc.update(
                "INSERT INTO program (title, description, location_text, start_at, end_at, capacity, fee,"
                        + " apply_open_at, apply_close_at, payment_deadline_minutes, overnight, status, created_by,"
                        + " created_at, updated_at) VALUES ('가을 백패킹', '설명', '설악산 입구', ?, ?, ?, 30000, ?, ?, 15, ?, ?, ?,"
                        + " ?, ?)",
                utc(startAt),
                utc(startAt.plus(Duration.ofDays(1))),
                capacity,
                utc(applyOpenAt),
                utc(applyCloseAt),
                overnight,
                status,
                adminId,
                utc(NOW),
                utc(NOW));
        return jdbc.queryForObject("SELECT MAX(id) FROM program", Long.class);
    }

    /** 신청 행을 직접 저장하고 그 ID를 돌려준다. */
    public long saveApplication(long programId, long memberId, String status) {
        jdbc.update(
                "INSERT INTO program_application (program_id, member_id, status, payment_due_at, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                programId,
                memberId,
                status,
                utc(NOW.plus(Duration.ofMinutes(15))),
                utc(NOW),
                utc(NOW));
        return jdbc.queryForObject("SELECT MAX(id) FROM program_application", Long.class);
    }

    /** 정원을 차지하는(결제 대기, 확정) 신청 수다. */
    public int activeCount(long programId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM program_application WHERE program_id = ?"
                        + " AND status IN ('PENDING_PAYMENT', 'CONFIRMED')",
                Integer.class,
                programId);
    }

    public int applicationCount(long programId, long memberId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM program_application WHERE program_id = ? AND member_id = ?",
                Integer.class,
                programId,
                memberId);
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
