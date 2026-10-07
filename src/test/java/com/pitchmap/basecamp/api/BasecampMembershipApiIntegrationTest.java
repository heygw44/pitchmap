package com.pitchmap.basecamp.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.application.EmailVerificationService;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
@AutoConfigureMockMvc
class BasecampMembershipApiIntegrationTest {

    private static final LocalDate START_DATE = BasecampApiFixture.DEFAULT_START_DATE;
    // 출발일 0시(한국)는 2026-10-19T15:00:00Z이고, 임박 탈퇴는 그 48시간 전인 2026-10-17T15:00:00Z부터다.
    private static final Instant EARLY_LEAVE_FROM = Instant.parse("2026-10-17T15:00:00Z");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private MutableClock clock;

    private BasecampApiFixture fixture;
    private Member leader;
    private Cookie leaderSession;
    private Member member;
    private Cookie memberSession;
    private long spotId;
    private long basecampId;

    @BeforeEach
    void setUp() {
        fixture = new BasecampApiFixture(mvc, memberRepository, passwordEncoder, emailVerificationService, jdbc);
        leader = fixture.saveMember();
        leaderSession = fixture.identityVerifiedSession(leader, "FEMALE");
        member = fixture.saveMember();
        memberSession = fixture.identityVerifiedSession(member, "MALE");
        spotId = fixture.insertSpot("개머리언덕", 37.25, 127.25);
        basecampId = fixture.insertBasecamp(leader, spotId, "RECRUITING", START_DATE);
        fixture.insertMemberRow(basecampId, member, "MEMBER", "ACTIVE");
        fixture.insertApplicationRow(basecampId, member, "APPROVED");
    }

    @Test
    @DisplayName("[F-13][BC-21] 멤버가 탈퇴하면 204이고 멤버 행이 LEFT가 되어 인원이 1명 줄며, 이벤트는 기록하지 않는다")
    void leaveMarksMemberLeft() {
        // when
        MvcTestResult result = leave(memberSession, basecampId);

        // then
        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(memberRow(member)).containsEntry("status", "LEFT").containsEntry("early_leave", false);
        assertThat(activeCount()).isEqualTo(1);
        assertThat(count("outbox_event")).isZero();
    }

    @Test
    @DisplayName("[F-13][BC-21] 캠프 리더가 탈퇴하면 409 BASECAMP_LEADER_CANNOT_LEAVE이고 멤버 행은 그대로다")
    void leaderCannotLeave() {
        // when
        MvcTestResult result = leave(leaderSession, basecampId);

        // then
        assertFailure(result, HttpStatus.CONFLICT, "BASECAMP_LEADER_CANNOT_LEAVE");
        assertThat(memberRow(leader)).containsEntry("status", "ACTIVE");
    }

    @Test
    @DisplayName(
            "[F-13][BC-21] 멤버가 아닌 회원의 탈퇴는 404 NOT_FOUND, 없는 베이스캠프는 404, 본인확인하지 않은 회원은 403 TRUST_LEVEL_INSUFFICIENT다")
    void leaveByNonMemberOrUnknown() {
        // given
        Cookie outsider = fixture.identityVerifiedSession(fixture.saveMember(), "MALE");
        Cookie unverified = fixture.verifiedSession(fixture.saveMember());

        // when
        MvcTestResult byOutsider = leave(outsider, basecampId);
        MvcTestResult unknown = leave(memberSession, basecampId + 1000);
        MvcTestResult byUnverified = leave(unverified, basecampId);

        // then
        assertFailure(byOutsider, HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertFailure(unknown, HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertFailure(byUnverified, HttpStatus.FORBIDDEN, "TRUST_LEVEL_INSUFFICIENT");
    }

    @Test
    @DisplayName("[F-13][BC-08] 정원이 차서 자동 마감된 베이스캠프에서 탈퇴하면 다시 RECRUITING이 된다")
    void leaveReopensAutoClosedBasecamp() {
        // given
        fixture.insertMemberRow(basecampId, fixture.saveMember(), "MEMBER", "ACTIVE");
        fixture.insertMemberRow(basecampId, fixture.saveMember(), "MEMBER", "ACTIVE");
        jdbc.update("UPDATE basecamp SET status = 'CLOSED', closed_reason = 'AUTO_FULL' WHERE id = ?", basecampId);

        // when
        MvcTestResult result = leave(memberSession, basecampId);

        // then
        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(basecampRow()).containsEntry("status", "RECRUITING").containsEntry("closed_reason", null);
    }

    @Test
    @DisplayName("[F-13][BC-08] 캠프 리더가 직접 마감한 베이스캠프에서 탈퇴해도 마감 그대로다")
    void leaveKeepsLeaderClosedBasecamp() {
        // given
        jdbc.update("UPDATE basecamp SET status = 'CLOSED', closed_reason = 'LEADER' WHERE id = ?", basecampId);

        // when
        MvcTestResult result = leave(memberSession, basecampId);

        // then
        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(basecampRow()).containsEntry("status", "CLOSED").containsEntry("closed_reason", "LEADER");
    }

    @Test
    @DisplayName("[F-13][BC-21] 확정된 뒤 출발 48시간 전부터 탈퇴하면 임박 탈퇴로 기록하고, 1초 전까지는 기록하지 않는다")
    void earlyLeaveIsRecordedWithin48Hours() {
        // given
        confirmBasecamp();
        Member early = fixture.saveMember();
        Cookie earlySession = fixture.identityVerifiedSession(early, "MALE");
        fixture.insertMemberRow(basecampId, early, "MEMBER", "ACTIVE");

        // when
        clock.setInstant(EARLY_LEAVE_FROM.minusSeconds(1));
        MvcTestResult outsideWindow = leave(memberSession, basecampId);
        clock.setInstant(EARLY_LEAVE_FROM);
        MvcTestResult insideWindow = leave(earlySession, basecampId);

        // then
        assertThat(outsideWindow).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(insideWindow).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(memberRow(member)).containsEntry("early_leave", false);
        assertThat(memberRow(early)).containsEntry("early_leave", true);
    }

    @Test
    @DisplayName("[F-13][BC-21] 취소된 베이스캠프에서 탈퇴하면 409 BASECAMP_INVALID_STATE다")
    void canceledBasecampRejectsLeave() {
        // given
        jdbc.update("UPDATE basecamp SET status = 'CANCELED' WHERE id = ?", basecampId);

        // when
        MvcTestResult result = leave(memberSession, basecampId);

        // then
        assertFailure(result, HttpStatus.CONFLICT, "BASECAMP_INVALID_STATE");
        assertThat(memberRow(member)).containsEntry("status", "ACTIVE");
    }

    @Test
    @DisplayName("[F-13][BC-07] 탈퇴한 회원이 다시 신청하면 409 BASECAMP_REAPPLY_NOT_ALLOWED다")
    void leftMemberCannotReapply() {
        // given
        assertThat(leave(memberSession, basecampId)).hasStatus(HttpStatus.NO_CONTENT);

        // when
        MvcTestResult result = apply(memberSession);

        // then
        assertFailure(result, HttpStatus.CONFLICT, "BASECAMP_REAPPLY_NOT_ALLOWED");
    }

    @Test
    @DisplayName("[F-13][BC-22] 캠프 리더가 강퇴하면 204이고 멤버 행이 KICKED가 되어 사유가 저장되며, 개인정보 없는 BASECAMP_KICKED 이벤트가 기록된다")
    void kickMarksMemberKickedAndRecordsEvent() throws Exception {
        // when
        MvcTestResult result = kick(leaderSession, basecampId, member.getId(), "NO_CONTACT");

        // then
        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(memberRow(member)).containsEntry("status", "KICKED").containsEntry("kick_reason", "NO_CONTACT");
        assertThat(activeCount()).isEqualTo(1);
        List<Map<String, Object>> events = jdbc.queryForList(
                "SELECT aggregate_type, aggregate_id, payload FROM outbox_event WHERE event_type = 'BASECAMP_KICKED'");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).get("aggregate_type")).isEqualTo("BASECAMP");
        assertThat(((Number) events.get(0).get("aggregate_id")).longValue()).isEqualTo(basecampId);
        String payload = (String) events.get(0).get("payload");
        JsonNode json = jsonMapper.readTree(payload);
        assertThat(Set.copyOf(json.propertyNames())).containsExactlyInAnyOrder("basecampId", "memberId", "reason");
        assertThat(json.get("basecampId").asLong()).isEqualTo(basecampId);
        assertThat(json.get("memberId").asLong()).isEqualTo(member.getId());
        assertThat(json.get("reason").asText()).isEqualTo("NO_CONTACT");
        assertThat(payload).doesNotContain(member.getEmail());
    }

    @Test
    @DisplayName("[F-13][BC-22] 캠프 리더가 아닌 회원이 강퇴하면 403 ACCESS_DENIED이고 멤버 행은 그대로다")
    void nonLeaderCannotKick() {
        // when
        MvcTestResult result = kick(memberSession, basecampId, member.getId(), "OTHER");

        // then
        assertFailure(result, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
        assertThat(memberRow(member)).containsEntry("status", "ACTIVE");
        assertThat(count("outbox_event")).isZero();
    }

    @Test
    @DisplayName("[F-13][BC-22] 확정된 베이스캠프에서는 강퇴할 수 없고 409 BASECAMP_INVALID_STATE다")
    void confirmedBasecampRejectsKick() {
        // given
        confirmBasecamp();

        // when
        MvcTestResult result = kick(leaderSession, basecampId, member.getId(), "OTHER");

        // then
        assertFailure(result, HttpStatus.CONFLICT, "BASECAMP_INVALID_STATE");
        assertThat(memberRow(member)).containsEntry("status", "ACTIVE");
    }

    @Test
    @DisplayName("[F-13][BC-22] 캠프 리더 자신을 강퇴하면 409 BASECAMP_LEADER_CANNOT_LEAVE, 멤버가 아닌 회원은 404 NOT_FOUND다")
    void kickTargetMustBeActiveNonLeaderMember() {
        // given
        Member outsider = fixture.saveMember();

        // when
        MvcTestResult leaderTarget = kick(leaderSession, basecampId, leader.getId(), "OTHER");
        MvcTestResult outsiderTarget = kick(leaderSession, basecampId, outsider.getId(), "OTHER");
        MvcTestResult unknownBasecamp = kick(leaderSession, basecampId + 1000, member.getId(), "OTHER");

        // then
        assertFailure(leaderTarget, HttpStatus.CONFLICT, "BASECAMP_LEADER_CANNOT_LEAVE");
        assertFailure(outsiderTarget, HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertFailure(unknownBasecamp, HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertThat(memberRow(leader)).containsEntry("status", "ACTIVE");
    }

    @Test
    @DisplayName("[F-13][BC-22] 강퇴 사유가 없거나 정해진 값이 아니면 400 INVALID_INPUT이고 멤버 행은 그대로다")
    void kickRequiresKnownReason() {
        // when
        MvcTestResult unknown = kick(leaderSession, basecampId, member.getId(), "BAD_REASON");
        MvcTestResult missing = mvc.post()
                .uri("/api/basecamps/" + basecampId + "/members/" + member.getId() + "/kick")
                .with(csrf())
                .cookie(leaderSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .exchange();

        // then
        assertFailure(unknown, HttpStatus.BAD_REQUEST, "INVALID_INPUT");
        assertFailure(missing, HttpStatus.BAD_REQUEST, "INVALID_INPUT");
        assertThat(memberRow(member)).containsEntry("status", "ACTIVE");
    }

    @Test
    @DisplayName("[F-13][BC-07] 강퇴된 회원이 다시 신청하면 409 BASECAMP_REAPPLY_NOT_ALLOWED다")
    void kickedMemberCannotReapply() {
        // given
        assertThat(kick(leaderSession, basecampId, member.getId(), "CONDITION_MISMATCH"))
                .hasStatus(HttpStatus.NO_CONTENT);

        // when
        MvcTestResult result = apply(memberSession);

        // then
        assertFailure(result, HttpStatus.CONFLICT, "BASECAMP_REAPPLY_NOT_ALLOWED");
    }

    @Test
    @DisplayName("[F-13][BC-08] 강퇴로 빈자리가 생기면 자동 마감된 베이스캠프가 다시 RECRUITING이 된다")
    void kickReopensAutoClosedBasecamp() {
        // given
        fixture.insertMemberRow(basecampId, fixture.saveMember(), "MEMBER", "ACTIVE");
        fixture.insertMemberRow(basecampId, fixture.saveMember(), "MEMBER", "ACTIVE");
        jdbc.update("UPDATE basecamp SET status = 'CLOSED', closed_reason = 'AUTO_FULL' WHERE id = ?", basecampId);

        // when
        MvcTestResult result = kick(leaderSession, basecampId, member.getId(), "INAPPROPRIATE_BEHAVIOR");

        // then
        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(basecampRow()).containsEntry("status", "RECRUITING");
    }

    // 출발일이 지나기 전까지 확정 상태로 둔다. 확정 시각은 테스트 시계의 기본 시각이다.
    private void confirmBasecamp() {
        jdbc.update(
                "UPDATE basecamp SET status = 'CONFIRMED', confirmed_at = '2026-10-05 03:00:00' WHERE id = ?",
                basecampId);
    }

    private MvcTestResult leave(Cookie session, long id) {
        return mvc.delete()
                .uri("/api/basecamps/" + id + "/members/me")
                .with(csrf())
                .cookie(session)
                .exchange();
    }

    private MvcTestResult kick(Cookie session, long id, long targetMemberId, String reason) {
        return mvc.post()
                .uri("/api/basecamps/" + id + "/members/" + targetMemberId + "/kick")
                .with(csrf())
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"%s\"}".formatted(reason))
                .exchange();
    }

    private MvcTestResult apply(Cookie session) {
        return mvc.post()
                .uri("/api/basecamps/" + basecampId + "/applications")
                .with(csrf())
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .exchange();
    }

    private Map<String, Object> memberRow(Member target) {
        return jdbc.queryForMap(
                "SELECT status, early_leave, kick_reason FROM basecamp_member WHERE basecamp_id = ? AND member_id = ?",
                basecampId,
                target.getId());
    }

    private Map<String, Object> basecampRow() {
        return jdbc.queryForMap("SELECT status, closed_reason FROM basecamp WHERE id = ?", basecampId);
    }

    private int activeCount() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM basecamp_member WHERE basecamp_id = ? AND status = 'ACTIVE'",
                Integer.class,
                basecampId);
    }

    private static void assertFailure(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
    }

    private int count(String table) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }
}
