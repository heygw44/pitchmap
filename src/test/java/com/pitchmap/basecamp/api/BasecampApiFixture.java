package com.pitchmap.basecamp.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.member.application.EmailVerificationService;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * 베이스캠프 조회 API 통합 테스트가 함께 쓰는 준비 코드다. 실제 로그인·이메일 인증·본인확인 흐름으로 세션을 만들고,
 * 베이스캠프와 멤버·신청 행은 SQL로 직접 넣어서 원하는 상태를 바로 만든다.
 */
final class BasecampApiFixture {

    static final String VALID_PASSWORD = "Valid-pass1";
    static final LocalDate DEFAULT_START_DATE = LocalDate.of(2026, 10, 20);
    private static final String SESSION_COOKIE = "SESSION";
    private static final String LOCAL_IP = "127.0.0.1";
    // 테스트 시계는 한국 날짜로 2026-10-05이다. 2007년생은 성인이고 연령대는 20대다.
    private static final int ADULT_BIRTH_YEAR = 2007;
    private static final String AT_DEFAULT_INSTANT = "2026-10-05 03:00:00";

    private final MockMvcTester mvc;
    private final MemberJpaRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final JdbcTemplate jdbc;
    private int identitySequence;

    BasecampApiFixture(
            MockMvcTester mvc,
            MemberJpaRepository memberRepository,
            PasswordEncoder passwordEncoder,
            EmailVerificationService emailVerificationService,
            JdbcTemplate jdbc) {
        this.mvc = mvc;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService = emailVerificationService;
        this.jdbc = jdbc;
    }

    Member saveMember() {
        return memberRepository.saveAndFlush(
                aMember().passwordHash(passwordEncoder.encode(VALID_PASSWORD)).build());
    }

    Cookie login(Member member) {
        MvcTestResult result = mvc.post()
                .uri("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(member.getEmail(), VALID_PASSWORD))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        Cookie session = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        return session;
    }

    // 이메일 인증을 마치고 성인으로 본인확인까지 해서 신뢰 단계 1인 세션을 만든다. 회원마다 식별 문자열을 다르게 줘야 같은 사람으로 보지 않는다.
    Cookie identityVerifiedSession(Member member, String gender) {
        Cookie session = login(member);
        String code = emailVerificationService
                .issueFor(member.getId(), LOCAL_IP)
                .orElseThrow()
                .code();
        MvcTestResult verified = mvc.post()
                .uri("/api/me/email-verification")
                .with(csrf())
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"%s\"}".formatted(code))
                .exchange();
        assertThat(verified).hasStatus(HttpStatus.OK);
        identitySequence++;
        MvcTestResult result = mvc.post()
                .uri("/api/me/identity-verification")
                .with(csrf())
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"birthYear\":%d,\"gender\":\"%s\",\"demoIdentityKey\":\"demo-member-%d\"}"
                        .formatted(ADULT_BIRTH_YEAR, gender, identitySequence))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        return session;
    }

    long insertSpot(String name, double lat, double lng) {
        jdbc.update(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                        + " VALUES ('BAKJI', ?, ST_GeomFromText(?, 4326), 60, 127, 'ACTIVE', NOW(6), NOW(6))",
                name,
                "POINT(%s %s)".formatted(lat, lng));
        return jdbc.queryForObject("SELECT MAX(id) FROM spot", Long.class);
    }

    // 캠프 리더의 멤버 행도 함께 넣는다. 종료일은 출발일 이틀 뒤, 정원은 4명이다.
    long insertBasecamp(Member leader, long spotId, String status, LocalDate startDate) {
        jdbc.update(
                "INSERT INTO basecamp (leader_id, spot_id, title, description, start_date, end_date, capacity, status,"
                        + " created_at, updated_at) VALUES (?, ?, '굴업도 주말 1박', '함께 가요', ?, ?, 4, ?, ?, ?)",
                leader.getId(),
                spotId,
                startDate,
                startDate.plusDays(2),
                status,
                AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT);
        long id = jdbc.queryForObject("SELECT MAX(id) FROM basecamp", Long.class);
        insertMemberRow(id, leader, "LEADER", "ACTIVE");
        return id;
    }

    void insertMemberRow(long basecampId, Member member, String role, String status) {
        jdbc.update(
                "INSERT INTO basecamp_member (basecamp_id, member_id, role, status, joined_at, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                basecampId,
                member.getId(),
                role,
                status,
                AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT);
    }

    void insertApplicationRow(long basecampId, Member applicant, String status) {
        jdbc.update(
                "INSERT INTO basecamp_application (basecamp_id, applicant_id, status, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?)",
                basecampId,
                applicant.getId(),
                status,
                AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT);
    }
}
