package com.pitchmap.basecamp.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.member.application.EmailVerificationService;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
@AutoConfigureMockMvc
class BasecampTransitionApiIntegrationTest {

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

    private BasecampApiFixture fixture;
    private Member leader;
    private Cookie leaderSession;
    private Member member;
    private Cookie memberSession;
    private long basecampId;

    @BeforeEach
    void setUp() {
        fixture = new BasecampApiFixture(mvc, memberRepository, passwordEncoder, emailVerificationService, jdbc);
        leader = fixture.saveMember();
        leaderSession = fixture.identityVerifiedSession(leader, "FEMALE");
        member = fixture.saveMember();
        memberSession = fixture.identityVerifiedSession(member, "MALE");
        long spotId = fixture.insertSpot("개머리언덕", 37.25, 127.25);
        basecampId = fixture.insertBasecamp(leader, spotId, "RECRUITING", BasecampApiFixture.DEFAULT_START_DATE);
    }

    @Test
    @DisplayName("[F-14] 캠프 리더가 모집 중인 베이스캠프를 마감하면 200 CLOSED이고 마감 사유는 LEADER다")
    void leaderClosesRecruitingBasecamp() {
        // when
        MvcTestResult result = post(leaderSession, "close");

        // then
        assertStatus(result, "CLOSED");
        assertThat(fixture.basecampRow(basecampId))
                .containsEntry("status", "CLOSED")
                .containsEntry("closed_reason", "LEADER");
    }

    @Test
    @DisplayName("[F-14] 모집 중이 아닌 베이스캠프를 마감하면 409 BASECAMP_INVALID_STATE다")
    void closeRejectsNonRecruitingBasecamp() {
        // given
        fixture.setClosedReason(basecampId, "CLOSED", "LEADER");

        // when
        MvcTestResult result = post(leaderSession, "close");

        // then
        assertFailure(result, HttpStatus.CONFLICT, "BASECAMP_INVALID_STATE");
    }

    @Test
    @DisplayName("[F-14] 직접 마감한 베이스캠프의 모집을 다시 열면 200 RECRUITING이고 마감 사유가 지워진다")
    void leaderReopensClosedBasecamp() {
        // given
        fixture.setClosedReason(basecampId, "CLOSED", "LEADER");

        // when
        MvcTestResult result = post(leaderSession, "reopen");

        // then
        assertStatus(result, "RECRUITING");
        assertThat(fixture.basecampRow(basecampId))
                .containsEntry("status", "RECRUITING")
                .containsEntry("closed_reason", null);
    }

    @Test
    @DisplayName("[F-14] 정원이 가득 찬 마감 베이스캠프를 다시 열면 409 BASECAMP_FULL이다")
    void reopenRejectsFullBasecamp() {
        // given
        fixture.setCapacity(basecampId, 2);
        fixture.insertMemberRow(basecampId, member, "MEMBER", "ACTIVE");
        fixture.setClosedReason(basecampId, "CLOSED", "LEADER");

        // when
        MvcTestResult result = post(leaderSession, "reopen");

        // then
        assertFailure(result, HttpStatus.CONFLICT, "BASECAMP_FULL");
        assertThat(fixture.basecampRow(basecampId)).containsEntry("status", "CLOSED");
    }

    @Test
    @DisplayName("[F-14] 모집 중인 베이스캠프를 다시 열면 409 BASECAMP_INVALID_STATE다")
    void reopenRejectsRecruitingBasecamp() {
        // when
        MvcTestResult result = post(leaderSession, "reopen");

        // then
        assertFailure(result, HttpStatus.CONFLICT, "BASECAMP_INVALID_STATE");
    }

    @Test
    @DisplayName(
            "[F-14][BC-12] 인원이 2명 이상이면 캠프 리더가 확정할 수 있고, 대기 신청은 만료되며 캠프 리더를 포함한 멤버 전원의 BASECAMP_CONFIRMED 이벤트가 기록된다")
    void leaderConfirmsWithEnoughMembers() throws Exception {
        // given
        fixture.insertMemberRow(basecampId, member, "MEMBER", "ACTIVE");
        Member applicant = fixture.saveMember();
        fixture.insertApplicationRow(basecampId, applicant, "PENDING");

        // when
        MvcTestResult result = post(leaderSession, "confirm");

        // then
        assertStatus(result, "CONFIRMED");
        assertThat(fixture.basecampRow(basecampId)).containsEntry("status", "CONFIRMED");
        assertThat(fixture.applicationStatus(basecampId, applicant)).isEqualTo("EXPIRED");
        List<Map<String, Object>> events = fixture.outboxEvents("BASECAMP_CONFIRMED");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).get("aggregate_type")).isEqualTo("BASECAMP");
        assertThat(((Number) events.get(0).get("aggregate_id")).longValue()).isEqualTo(basecampId);
        JsonNode payload = jsonMapper.readTree((String) events.get(0).get("payload"));
        assertThat(Set.copyOf(payload.propertyNames())).containsExactlyInAnyOrder("basecampId", "memberIds");
        assertThat(payload.get("basecampId").asLong()).isEqualTo(basecampId);
        assertThat(memberIds(payload)).containsExactlyInAnyOrder(leader.getId(), member.getId());
    }

    @Test
    @DisplayName("[F-14][BC-12] 캠프 리더 혼자이면 확정이 409 BASECAMP_NOT_ENOUGH_MEMBERS이고 이벤트를 기록하지 않는다")
    void confirmRejectsLeaderOnly() {
        // when
        MvcTestResult result = post(leaderSession, "confirm");

        // then
        assertFailure(result, HttpStatus.CONFLICT, "BASECAMP_NOT_ENOUGH_MEMBERS");
        assertThat(fixture.basecampRow(basecampId)).containsEntry("status", "RECRUITING");
        assertThat(fixture.outboxCount()).isZero();
    }

    @Test
    @DisplayName("[F-14][BC-20] 캠프 리더가 취소하면 200 CANCELED이고 취소 사유는 LEADER이며, 캠프 리더를 뺀 멤버의 BASECAMP_CANCELED 이벤트가 기록된다")
    void leaderCancelsAndRecordsEventWithoutLeader() throws Exception {
        // given
        fixture.insertMemberRow(basecampId, member, "MEMBER", "ACTIVE");
        Member applicant = fixture.saveMember();
        fixture.insertApplicationRow(basecampId, applicant, "PENDING");

        // when
        MvcTestResult result = post(leaderSession, "cancel");

        // then
        assertStatus(result, "CANCELED");
        assertThat(fixture.basecampRow(basecampId))
                .containsEntry("status", "CANCELED")
                .containsEntry("cancel_reason", "LEADER");
        assertThat(fixture.applicationStatus(basecampId, applicant)).isEqualTo("EXPIRED");
        List<Map<String, Object>> events = fixture.outboxEvents("BASECAMP_CANCELED");
        assertThat(events).hasSize(1);
        JsonNode payload = jsonMapper.readTree((String) events.get(0).get("payload"));
        assertThat(payload.get("basecampId").asLong()).isEqualTo(basecampId);
        assertThat(memberIds(payload)).containsExactly(member.getId());
    }

    @Test
    @DisplayName("[F-14][BC-20] 이미 취소되었거나 완료된 베이스캠프를 취소하면 409 BASECAMP_INVALID_STATE이고 이벤트를 기록하지 않는다")
    void cancelRejectsFinishedBasecamp() {
        // given
        fixture.setStatus(basecampId, "CANCELED");
        long completed = fixture.insertBasecamp(
                leader, fixture.insertSpot("다른언덕", 37.3, 127.3), "COMPLETED", BasecampApiFixture.DEFAULT_START_DATE);

        // when
        MvcTestResult onCanceled = post(leaderSession, "cancel");
        MvcTestResult onCompleted = post(leaderSession, completed, "cancel");

        // then
        assertFailure(onCanceled, HttpStatus.CONFLICT, "BASECAMP_INVALID_STATE");
        assertFailure(onCompleted, HttpStatus.CONFLICT, "BASECAMP_INVALID_STATE");
        assertThat(fixture.outboxCount()).isZero();
    }

    @Test
    @DisplayName("[F-14] 캠프 리더가 아닌 멤버나 외부 회원이 요청하면 403 ACCESS_DENIED이고 상태는 그대로다")
    void nonLeaderIsDenied() {
        // given
        fixture.insertMemberRow(basecampId, member, "MEMBER", "ACTIVE");
        Cookie outsider = fixture.identityVerifiedSession(fixture.saveMember(), "MALE");

        // when
        for (String action : List.of("close", "reopen", "confirm", "cancel")) {
            // then
            assertFailure(post(memberSession, action), HttpStatus.FORBIDDEN, "ACCESS_DENIED");
            assertFailure(post(outsider, action), HttpStatus.FORBIDDEN, "ACCESS_DENIED");
        }
        assertThat(fixture.basecampRow(basecampId)).containsEntry("status", "RECRUITING");
        assertThat(fixture.outboxCount()).isZero();
    }

    @Test
    @DisplayName("[F-14] 없는 베이스캠프에 요청하면 404 NOT_FOUND다")
    void unknownBasecampIsNotFound() {
        // when
        MvcTestResult result = post(leaderSession, basecampId + 1000, "close");

        // then
        assertFailure(result, HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    private static List<Long> memberIds(JsonNode payload) {
        return payload.get("memberIds").valueStream().map(JsonNode::asLong).toList();
    }

    private MvcTestResult post(Cookie session, String action) {
        return post(session, basecampId, action);
    }

    private MvcTestResult post(Cookie session, long id, String action) {
        return mvc.post()
                .uri("/api/basecamps/" + id + "/" + action)
                .with(csrf())
                .cookie(session)
                .exchange();
    }

    private void assertStatus(MvcTestResult result, String status) {
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.basecampId").isEqualTo((int) basecampId);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(status);
    }

    private static void assertFailure(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
    }
}
