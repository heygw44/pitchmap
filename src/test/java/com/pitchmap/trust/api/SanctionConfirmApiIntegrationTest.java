package com.pitchmap.trust.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.notification.application.OutboxPublisher;
import com.pitchmap.trust.application.CompanionReviewFixture;
import com.pitchmap.trust.application.SanctionConfirmCommand;
import com.pitchmap.trust.application.SanctionConfirmService;
import com.pitchmap.trust.domain.SanctionType;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@AutoConfigureMockMvc
class SanctionConfirmApiIntegrationTest {

    private static final String PASSWORD = "Valid-pass1";
    private static final String SESSION_COOKIE = "SESSION";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private SanctionConfirmService sanctionConfirmService;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    private CompanionReviewFixture fixture;
    private long adminId;
    private long targetId;

    @BeforeEach
    void setUp() {
        fixture = new CompanionReviewFixture(jdbc, memberRepository);
        adminId = fixture.saveVerifiedMember(TestSequence.nickname());
        targetId = fixture.saveVerifiedMember(TestSequence.nickname());
    }

    @Test
    @DisplayName("[SN-12][SN-13] 정지를 확정하면 기존 세션의 다음 요청은 401이고, 발행기가 돌면 리더인 베이스캠프는 취소되고 멤버인 베이스캠프에서는 빠진다")
    void suspensionInvalidatesSessionAndCleansUpBasecamps() {
        Cookie session = login(targetId);
        Cookie secondSession = login(targetId);
        assertThat(mvc.get().uri("/api/me").cookie(session).exchange()).hasStatus(HttpStatus.OK);
        long ledBasecamp = ledBasecamp(targetId);
        long ledMember = joinMember(ledBasecamp);
        long joinedBasecamp = fixture.saveBasecamp("CONFIRMED", null);
        long otherMember = joinMember(joinedBasecamp);
        fixture.insertMember(joinedBasecamp, targetId, "MEMBER", "ACTIVE");
        long appliedBasecamp = fixture.saveBasecamp("RECRUITING", null);
        fixture.insertApplication(appliedBasecamp, targetId, "PENDING");
        long unrelatedBasecamp = fixture.saveBasecamp("RECRUITING", null);

        sanctionConfirmService.confirm(
                new SanctionConfirmCommand(targetId, null, SanctionType.PERMANENT, "금전 요구", adminId));

        assertThat(sessionCount(targetId)).isZero();
        assertThat(mvc.get().uri("/api/me").cookie(session).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/me").cookie(secondSession).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        MvcTestResult login = loginRequest(targetId);
        assertThat(login).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(login).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_SUSPENDED");
        // 발행기가 돌기 전에는 베이스캠프가 그대로다.
        assertThat(basecampStatus(ledBasecamp)).isEqualTo("RECRUITING");

        outboxPublisher.publishPending();

        assertThat(jdbc.queryForObject(
                        "SELECT CONCAT(status, ':', cancel_reason) FROM basecamp WHERE id = ?",
                        String.class,
                        ledBasecamp))
                .isEqualTo("CANCELED:LEADER_SANCTIONED");
        assertThat(jdbc.queryForObject(
                        "SELECT CONCAT(status, ':', early_leave) FROM basecamp_member WHERE basecamp_id = ? AND member_id = ?",
                        String.class,
                        joinedBasecamp,
                        targetId))
                .isEqualTo("LEFT:0");
        assertThat(basecampStatus(joinedBasecamp)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM basecamp_application WHERE basecamp_id = ? AND applicant_id = ?",
                        String.class,
                        appliedBasecamp,
                        targetId))
                .isEqualTo("CANCELED");
        assertThat(basecampStatus(appliedBasecamp)).isEqualTo("RECRUITING");
        assertThat(basecampStatus(unrelatedBasecamp)).isEqualTo("RECRUITING");
        assertThat(eventStatus("SANCTION_BASECAMP_CLEANUP")).isEqualTo("PUBLISHED");
        assertThat(eventStatus("SANCTION_PROGRAM_CLEANUP")).isEqualTo("PUBLISHED");
        assertThat(memberIdsOf("BASECAMP_CANCELED", ledBasecamp)).containsExactly(ledMember);
        assertThat(memberIdsOf("BASECAMP_MEMBER_CHANGED", joinedBasecamp))
                .containsExactly(fixture.leaderIdOf(joinedBasecamp), otherMember);
    }

    @Test
    @DisplayName("[SN-10] 경고는 세션을 지우지 않고 베이스캠프도 건드리지 않으며, 정리 이벤트 없이 제재 알림 이벤트만 처리한다")
    void warningKeepsSessionAndBasecamps() {
        Cookie session = login(targetId);
        long ledBasecamp = ledBasecamp(targetId);

        sanctionConfirmService.confirm(new SanctionConfirmCommand(targetId, null, SanctionType.WARNING, "욕설", adminId));
        outboxPublisher.publishPending();

        assertThat(mvc.get().uri("/api/me").cookie(session).exchange()).hasStatus(HttpStatus.OK);
        assertThat(basecampStatus(ledBasecamp)).isEqualTo("RECRUITING");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM outbox_event WHERE event_type = 'SANCTION_BASECAMP_CLEANUP'",
                        Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM outbox_event WHERE event_type = 'SANCTION_PROGRAM_CLEANUP'",
                        Integer.class))
                .isZero();
        assertThat(eventStatus("SANCTION_CONFIRMED")).isEqualTo("PUBLISHED");
    }

    private long ledBasecamp(long leaderId) {
        long basecampId = fixture.saveBasecamp("RECRUITING", null);
        jdbc.update(
                "UPDATE basecamp_member SET member_id = ? WHERE basecamp_id = ? AND role = 'LEADER'",
                leaderId,
                basecampId);
        jdbc.update("UPDATE basecamp SET leader_id = ? WHERE id = ?", leaderId, basecampId);
        return basecampId;
    }

    private long joinMember(long basecampId) {
        long memberId = fixture.saveVerifiedMember(TestSequence.nickname());
        fixture.insertMember(basecampId, memberId, "MEMBER", "ACTIVE");
        return memberId;
    }

    private String basecampStatus(long basecampId) {
        return jdbc.queryForObject("SELECT status FROM basecamp WHERE id = ?", String.class, basecampId);
    }

    private String eventStatus(String eventType) {
        return jdbc.queryForObject("SELECT status FROM outbox_event WHERE event_type = ?", String.class, eventType);
    }

    // 이벤트 payload의 알림 대상 회원 ID를 읽는다. MySQL의 JSON 열은 공백을 정리해 돌려주므로 문자열이 아니라 JSON으로 읽는다.
    private List<Long> memberIdsOf(String eventType, long aggregateId) {
        List<String> payloads = jdbc.queryForList(
                "SELECT payload FROM outbox_event WHERE event_type = ? AND aggregate_id = ?",
                String.class,
                eventType,
                aggregateId);
        assertThat(payloads).hasSize(1);
        List<Number> ids = JsonPath.read(payloads.get(0), "$.memberIds");
        return ids.stream().map(Number::longValue).toList();
    }

    private int sessionCount(long memberId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?",
                Integer.class,
                String.valueOf(memberId));
    }

    private Cookie login(long memberId) {
        MvcTestResult result = loginRequest(memberId);
        assertThat(result).hasStatus(HttpStatus.OK);
        Cookie session = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        return session;
    }

    private MvcTestResult loginRequest(long memberId) {
        jdbc.update("UPDATE member SET password_hash = ? WHERE id = ?", passwordEncoder.encode(PASSWORD), memberId);
        String email = jdbc.queryForObject("SELECT email FROM member WHERE id = ?", String.class, memberId);
        String body = "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD);
        return mvc.post()
                .uri("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }
}
