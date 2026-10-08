package com.pitchmap.basecamp.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.member.application.EmailVerificationService;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
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
class BasecampApprovalApiIntegrationTest {

    private static final LocalDate START_DATE = BasecampApiFixture.DEFAULT_START_DATE;

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
    private Member applicant;
    private Cookie applicantSession;
    private long spotId;
    private long basecampId;

    @BeforeEach
    void setUp() {
        fixture = new BasecampApiFixture(mvc, memberRepository, passwordEncoder, emailVerificationService, jdbc);
        leader = fixture.saveMember();
        leaderSession = fixture.identityVerifiedSession(leader, "FEMALE");
        applicant = fixture.saveMember();
        // 신청자는 2007년생 남성(20대)으로 본인확인한 단계 1 회원이다.
        applicantSession = fixture.identityVerifiedSession(applicant, "MALE");
        spotId = fixture.insertSpot("개머리언덕", 37.25, 127.25);
        basecampId = fixture.insertBasecamp(leader, spotId, "RECRUITING", START_DATE);
    }

    @Test
    @DisplayName("[F-13][BC-08] 캠프 리더가 승인하면 200과 인원·상태를 응답하고, 신청은 APPROVED가 되며 신청자가 ACTIVE 멤버 행으로 들어간다")
    void approveAddsActiveMember() {
        // given
        long applicationId = pendingApplication(applicant);

        // when
        MvcTestResult result = approve(leaderSession, basecampId, applicationId);

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("{ \"headcount\": 2, \"status\": \"RECRUITING\" }");
        assertThat(applicationStatus(applicationId)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject(
                        "SELECT decided_at IS NOT NULL FROM basecamp_application WHERE id = ?",
                        Boolean.class,
                        applicationId))
                .isTrue();
        assertThat(memberRow(applicant)).containsEntry("role", "MEMBER").containsEntry("status", "ACTIVE");
    }

    @Test
    @DisplayName("[F-13][BC-08] 마지막 자리를 승인하면 status가 CLOSED이고 마감 이유는 AUTO_FULL이다")
    void approveLastSeatClosesAutomatically() {
        // given
        fixture.insertMemberRow(basecampId, fixture.saveMember(), "MEMBER", "ACTIVE");
        fixture.insertMemberRow(basecampId, fixture.saveMember(), "MEMBER", "ACTIVE");
        long applicationId = pendingApplication(applicant);

        // when
        MvcTestResult result = approve(leaderSession, basecampId, applicationId);

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("{ \"headcount\": 4, \"status\": \"CLOSED\" }");
        Map<String, Object> basecamp =
                jdbc.queryForMap("SELECT status, closed_reason FROM basecamp WHERE id = ?", basecampId);
        assertThat(basecamp).containsEntry("status", "CLOSED").containsEntry("closed_reason", "AUTO_FULL");
    }

    @Test
    @DisplayName("[F-13][BC-08] 캠프 리더가 아닌 회원이 승인하거나 거절하면 403 ACCESS_DENIED이고 신청은 대기로 남는다")
    void nonLeaderCannotDecide() {
        // given
        long applicationId = pendingApplication(applicant);

        // when
        MvcTestResult approved = approve(applicantSession, basecampId, applicationId);
        MvcTestResult rejected = reject(applicantSession, basecampId, applicationId);

        // then
        assertFailure(approved, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
        assertFailure(rejected, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
        assertThat(applicationStatus(applicationId)).isEqualTo("PENDING");
        assertThat(count("outbox_event")).isZero();
    }

    @Test
    @DisplayName("[F-13][BC-08] 없는 베이스캠프나 다른 베이스캠프의 신청을 승인·거절하면 404 NOT_FOUND다")
    void unknownBasecampOrForeignApplicationIsNotFound() {
        // given
        long applicationId = pendingApplication(applicant);
        long otherBasecampId = fixture.insertBasecamp(leader, spotId, "RECRUITING", START_DATE.plusDays(10));

        // when
        MvcTestResult unknownBasecamp = approve(leaderSession, basecampId + 1000, applicationId);
        MvcTestResult foreignApproval = approve(leaderSession, otherBasecampId, applicationId);
        MvcTestResult foreignRejection = reject(leaderSession, otherBasecampId, applicationId);

        // then
        assertFailure(unknownBasecamp, HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertFailure(foreignApproval, HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertFailure(foreignRejection, HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertThat(applicationStatus(applicationId)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("[F-13][BC-08] 대기가 아닌 신청(거절·취소·이미 승인)은 409 BASECAMP_INVALID_STATE다")
    void nonPendingApplicationIsInvalidState() {
        // given
        long rejected = applicationWithStatus("REJECTED");
        long canceled = applicationWithStatus("CANCELED");
        long approved = applicationWithStatus("APPROVED");

        // when
        // then
        for (long applicationId : List.of(rejected, canceled, approved)) {
            assertFailure(
                    approve(leaderSession, basecampId, applicationId), HttpStatus.CONFLICT, "BASECAMP_INVALID_STATE");
            assertFailure(
                    reject(leaderSession, basecampId, applicationId), HttpStatus.CONFLICT, "BASECAMP_INVALID_STATE");
        }
        assertThat(count("outbox_event")).isZero();
    }

    @Test
    @DisplayName("[F-13][BC-08] 마감된 베이스캠프의 신청은 승인·거절할 수 없고 409 BASECAMP_INVALID_STATE이며 신청은 대기로 남는다")
    void closedBasecampCannotDecide() {
        // given
        long applicationId = pendingApplication(applicant);
        jdbc.update("UPDATE basecamp SET status = 'CLOSED', closed_reason = 'LEADER' WHERE id = ?", basecampId);

        // when
        MvcTestResult approved = approve(leaderSession, basecampId, applicationId);
        MvcTestResult rejected = reject(leaderSession, basecampId, applicationId);

        // then
        assertFailure(approved, HttpStatus.CONFLICT, "BASECAMP_INVALID_STATE");
        assertFailure(rejected, HttpStatus.CONFLICT, "BASECAMP_INVALID_STATE");
        assertThat(applicationStatus(applicationId)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("[F-13][BC-05] 신청한 뒤 합류 조건이 바뀌어 신청자가 충족하지 못하면 403 BASECAMP_CONDITION_NOT_MET이고 신청은 대기로 남는다")
    void conditionIsCheckedAgainAtApproval() {
        // given
        long applicationId = pendingApplication(applicant);
        jdbc.update("UPDATE basecamp SET min_trust_level = 2 WHERE id = ?", basecampId);

        // when
        MvcTestResult result = approve(leaderSession, basecampId, applicationId);

        // then
        assertFailure(result, HttpStatus.FORBIDDEN, "BASECAMP_CONDITION_NOT_MET");
        assertThat(applicationStatus(applicationId)).isEqualTo("PENDING");
        assertThat(memberRow(applicant)).isNull();
        assertThat(count("outbox_event")).isZero();
    }

    @Test
    @DisplayName("[F-13][BC-05] 연령대와 성별 조건도 승인할 때 다시 확인한다")
    void ageGroupAndGenderAreCheckedAgainAtApproval() {
        // given
        long applicationId = pendingApplication(applicant);
        jdbc.update("UPDATE basecamp SET age_group_min = 30, age_group_max = 40 WHERE id = ?", basecampId);

        // when
        MvcTestResult outOfAgeRange = approve(leaderSession, basecampId, applicationId);
        jdbc.update("UPDATE basecamp SET age_group_min = NULL, age_group_max = NULL WHERE id = ?", basecampId);
        jdbc.update("UPDATE basecamp SET same_gender_only = TRUE, required_gender = 'FEMALE' WHERE id = ?", basecampId);
        MvcTestResult otherGender = approve(leaderSession, basecampId, applicationId);

        // then
        assertFailure(outOfAgeRange, HttpStatus.FORBIDDEN, "BASECAMP_CONDITION_NOT_MET");
        assertFailure(otherGender, HttpStatus.FORBIDDEN, "BASECAMP_CONDITION_NOT_MET");
        assertThat(applicationStatus(applicationId)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("[F-13][BC-05] 신청자의 신뢰 단계가 기본 단계 1 미만이면 BASECAMP_CONDITION_NOT_MET이다")
    void applicantBelowBaseTrustLevelIsConditionNotMet() {
        // given
        Member unverified = fixture.saveMember();
        fixture.verifiedSession(unverified);
        long applicationId = pendingApplication(unverified);

        // when
        MvcTestResult result = approve(leaderSession, basecampId, applicationId);

        // then
        assertFailure(result, HttpStatus.FORBIDDEN, "BASECAMP_CONDITION_NOT_MET");
        assertThat(applicationStatus(applicationId)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("[F-13][BC-06] 신청자가 같은 기간에 확정된 다른 베이스캠프의 멤버가 되었으면 409 BASECAMP_DATE_CONFLICT이고 신청은 대기로 남는다")
    void dateConflictIsCheckedAgainAtApproval() {
        // given
        long applicationId = pendingApplication(applicant);
        long confirmedId = fixture.insertBasecamp(fixture.saveMember(), spotId, "CONFIRMED", START_DATE.minusDays(1));
        fixture.insertMemberRow(confirmedId, applicant, "MEMBER", "ACTIVE");

        // when
        MvcTestResult result = approve(leaderSession, basecampId, applicationId);

        // then
        assertFailure(result, HttpStatus.CONFLICT, "BASECAMP_DATE_CONFLICT");
        assertThat(applicationStatus(applicationId)).isEqualTo("PENDING");
        assertThat(count("outbox_event")).isZero();
    }

    @Test
    @DisplayName("[F-13][BC-08] 승인하면 BASECAMP_APPROVED 이벤트가 개인정보 없이 기록된다")
    void approveRecordsEvent() throws Exception {
        // given
        long applicationId = pendingApplication(applicant);

        // when
        approve(leaderSession, basecampId, applicationId);

        // then
        assertDecisionEvent("BASECAMP_APPROVED", applicationId);
    }

    @Test
    @DisplayName("[F-13][BC-07] 거절하면 200과 REJECTED를 응답하고 BASECAMP_REJECTED 이벤트가 개인정보 없이 기록되며 멤버는 늘지 않는다")
    void rejectRecordsEventAndKeepsMembers() throws Exception {
        // given
        long applicationId = pendingApplication(applicant);

        // when
        MvcTestResult result = reject(leaderSession, basecampId, applicationId);

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result)
                .bodyJson()
                .isStrictlyEqualTo("{ \"applicationId\": %d, \"status\": \"REJECTED\" }".formatted(applicationId));
        assertThat(applicationStatus(applicationId)).isEqualTo("REJECTED");
        assertThat(memberRow(applicant)).isNull();
        assertDecisionEvent("BASECAMP_REJECTED", applicationId);
    }

    @Test
    @DisplayName("[F-13][BC-07] 거절된 회원이 다시 신청하면 409 BASECAMP_REAPPLY_NOT_ALLOWED다")
    void rejectedApplicantCannotReapply() {
        // given
        long applicationId = pendingApplication(applicant);
        assertThat(reject(leaderSession, basecampId, applicationId)).hasStatus(HttpStatus.OK);

        // when
        MvcTestResult result = mvc.post()
                .uri("/api/basecamps/" + basecampId + "/applications")
                .with(csrf())
                .cookie(applicantSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .exchange();

        // then
        assertFailure(result, HttpStatus.CONFLICT, "BASECAMP_REAPPLY_NOT_ALLOWED");
    }

    @Test
    @DisplayName("[F-13] 신청 목록은 status를 생략하면 PENDING만 신청이 오래된 순서로 주고, 신청자 프로필에 이메일과 출생연도가 없다")
    void listDefaultsToPendingOldestFirst() throws Exception {
        // given
        Member second = fixture.saveMember();
        fixture.identityVerifiedSession(second, "FEMALE");
        long firstId = pendingApplication(applicant);
        long secondId = pendingApplication(second);
        applicationWithStatus("REJECTED");

        // when
        MvcTestResult result = list(leaderSession, basecampId, "");

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        JsonNode body = jsonMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("content")).hasSize(2);
        assertThat(body.get("page").asInt()).isZero();
        assertThat(body.get("size").asInt()).isEqualTo(20);
        assertThat(body.get("hasNext").asBoolean()).isFalse();
        JsonNode first = body.get("content").get(0);
        assertThat(first.get("applicationId").asLong()).isEqualTo(firstId);
        assertThat(body.get("content").get(1).get("applicationId").asLong()).isEqualTo(secondId);
        assertThat(first.get("status").asText()).isEqualTo("PENDING");
        assertThat(first.get("appliedAt").asText()).isEqualTo("2026-10-05T03:00:00Z");
        assertThat(Set.copyOf(first.get("applicant").propertyNames()))
                .containsExactlyInAnyOrder(
                        "memberId",
                        "nickname",
                        "ageGroup",
                        "ageGroupVerified",
                        "gender",
                        "genderVerified",
                        "trustLevel",
                        "completedCompanions");
        assertThat(first.get("applicant").get("memberId").asLong()).isEqualTo(applicant.getId());
        assertThat(first.get("applicant").get("ageGroup").asText()).isEqualTo("TWENTIES");
        assertThat(first.get("applicant").get("ageGroupVerified").asBoolean()).isTrue();
        assertThat(first.get("applicant").get("trustLevel").asInt()).isEqualTo(1);
        String raw = result.getResponse().getContentAsString();
        assertThat(raw).doesNotContain(applicant.getEmail()).doesNotContainIgnoringCase("email");
        assertThat(raw).doesNotContainIgnoringCase("birthYear");
    }

    @Test
    @DisplayName("[F-13] 신청 목록에 status를 지정하면 그 상태의 신청만 준다")
    void listFiltersByStatus() throws Exception {
        // given
        pendingApplication(applicant);
        long rejectedId = applicationWithStatus("REJECTED");

        // when
        MvcTestResult result = list(leaderSession, basecampId, "?status=REJECTED");

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        JsonNode content =
                jsonMapper.readTree(result.getResponse().getContentAsString()).get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("applicationId").asLong()).isEqualTo(rejectedId);
        assertThat(content.get(0).get("status").asText()).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("[F-13] 신청 목록은 page와 size로 나누고 다음 페이지가 있으면 hasNext가 true다")
    void listPagesWithHasNext() throws Exception {
        // given
        long firstId = pendingApplication(applicant);
        long secondId = pendingApplication(fixture.saveMember());
        long thirdId = pendingApplication(fixture.saveMember());

        // when
        JsonNode firstPage = json(list(leaderSession, basecampId, "?page=0&size=2"));
        JsonNode lastPage = json(list(leaderSession, basecampId, "?page=1&size=2"));

        // then
        assertThat(firstPage.get("hasNext").asBoolean()).isTrue();
        assertThat(firstPage.get("size").asInt()).isEqualTo(2);
        assertThat(ids(firstPage)).containsExactly(firstId, secondId);
        assertThat(lastPage.get("hasNext").asBoolean()).isFalse();
        assertThat(lastPage.get("page").asInt()).isEqualTo(1);
        assertThat(ids(lastPage)).containsExactly(thirdId);
    }

    @Test
    @DisplayName(
            "[F-13] 신청 목록은 캠프 리더만 볼 수 있다. 다른 회원은 403 ACCESS_DENIED, 없는 베이스캠프는 404 NOT_FOUND, 허용하지 않는 status는 400 INVALID_INPUT이다")
    void listAuthorizationAndValidation() {
        // given
        pendingApplication(applicant);

        // when
        MvcTestResult byOther = list(applicantSession, basecampId, "");
        MvcTestResult unknown = list(leaderSession, basecampId + 1000, "");
        MvcTestResult badStatus = list(leaderSession, basecampId, "?status=DONE");

        // then
        assertFailure(byOther, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
        assertFailure(unknown, HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertFailure(badStatus, HttpStatus.BAD_REQUEST, "INVALID_INPUT");
    }

    private void assertDecisionEvent(String eventType, long applicationId) throws Exception {
        List<Map<String, Object>> events = jdbc.queryForList(
                "SELECT aggregate_type, aggregate_id, payload FROM outbox_event WHERE event_type = ?", eventType);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).get("aggregate_type")).isEqualTo("BASECAMP");
        assertThat(((Number) events.get(0).get("aggregate_id")).longValue()).isEqualTo(basecampId);
        String payload = (String) events.get(0).get("payload");
        JsonNode json = jsonMapper.readTree(payload);
        assertThat(Set.copyOf(json.propertyNames()))
                .containsExactlyInAnyOrder("applicationId", "applicantId", "basecampId");
        assertThat(json.get("applicationId").asLong()).isEqualTo(applicationId);
        assertThat(json.get("applicantId").asLong()).isEqualTo(applicant.getId());
        assertThat(json.get("basecampId").asLong()).isEqualTo(basecampId);
        assertThat(payload).doesNotContain(applicant.getEmail());
    }

    private long pendingApplication(Member member) {
        fixture.insertApplicationRow(basecampId, member, "PENDING");
        return jdbc.queryForObject("SELECT MAX(id) FROM basecamp_application", Long.class);
    }

    private long applicationWithStatus(String status) {
        fixture.insertApplicationRow(basecampId, fixture.saveMember(), status);
        return jdbc.queryForObject("SELECT MAX(id) FROM basecamp_application", Long.class);
    }

    private MvcTestResult approve(Cookie session, long id, long applicationId) {
        return mvc.post()
                .uri("/api/basecamps/" + id + "/applications/" + applicationId + "/approve")
                .with(csrf())
                .cookie(session)
                .exchange();
    }

    private MvcTestResult reject(Cookie session, long id, long applicationId) {
        return mvc.post()
                .uri("/api/basecamps/" + id + "/applications/" + applicationId + "/reject")
                .with(csrf())
                .cookie(session)
                .exchange();
    }

    private MvcTestResult list(Cookie session, long id, String query) {
        return mvc.get()
                .uri("/api/basecamps/" + id + "/applications" + query)
                .cookie(session)
                .exchange();
    }

    private JsonNode json(MvcTestResult result) throws Exception {
        assertThat(result).hasStatus(HttpStatus.OK);
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }

    private static List<Long> ids(JsonNode page) {
        return page.get("content")
                .valueStream()
                .map(node -> node.get("applicationId").asLong())
                .toList();
    }

    private Map<String, Object> memberRow(Member member) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT role, status FROM basecamp_member WHERE basecamp_id = ? AND member_id = ?",
                basecampId,
                member.getId());
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String applicationStatus(long applicationId) {
        return jdbc.queryForObject("SELECT status FROM basecamp_application WHERE id = ?", String.class, applicationId);
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
