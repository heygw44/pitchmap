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
import java.util.concurrent.atomic.AtomicLong;
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
class BasecampApplyApiIntegrationTest {

    private static final LocalDate START_DATE = BasecampApiFixture.DEFAULT_START_DATE;
    private static final String MESSAGE = "같이 가고 싶어요";

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
    private Member applicant;
    private Cookie applicantSession;
    private long spotId;
    private long basecampId;

    @BeforeEach
    void setUp() {
        fixture = new BasecampApiFixture(mvc, memberRepository, passwordEncoder, emailVerificationService, jdbc);
        leader = fixture.saveMember();
        applicant = fixture.saveMember();
        // 신청자는 2007년생 남성(20대)으로 본인확인한다.
        applicantSession = fixture.identityVerifiedSession(applicant, "MALE");
        spotId = fixture.insertSpot("개머리언덕", 37.25, 127.25);
        basecampId = fixture.insertBasecamp(leader, spotId, "RECRUITING", START_DATE);
    }

    @Test
    @DisplayName("[F-13][BC-06] 본인확인한 회원이 신청하면 201이고 PENDING 신청 행이 저장되며, 캠프 리더에게 알릴 BASECAMP_APPLIED 이벤트가 개인정보 없이 기록된다")
    void applySavesPendingApplicationAndRecordsEvent() throws Exception {
        // when
        MvcTestResult result = apply(applicantSession, basecampId, MESSAGE);

        // then
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("PENDING");
        long applicationId = applicationIdOf(result);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT basecamp_id, applicant_id, message, status FROM basecamp_application WHERE id = ?",
                applicationId);
        assertThat(((Number) row.get("basecamp_id")).longValue()).isEqualTo(basecampId);
        assertThat(((Number) row.get("applicant_id")).longValue()).isEqualTo(applicant.getId());
        assertThat(row.get("message")).isEqualTo(MESSAGE);
        assertThat(row.get("status")).isEqualTo("PENDING");

        List<Map<String, Object>> events =
                jdbc.queryForList("SELECT event_type, aggregate_type, aggregate_id, payload FROM outbox_event"
                        + " WHERE event_type = 'BASECAMP_APPLIED'");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).get("aggregate_type")).isEqualTo("BASECAMP");
        assertThat(((Number) events.get(0).get("aggregate_id")).longValue()).isEqualTo(basecampId);
        String payload = (String) events.get(0).get("payload");
        JsonNode json = jsonMapper.readTree(payload);
        assertThat(Set.copyOf(json.propertyNames()))
                .containsExactlyInAnyOrder("applicationId", "applicantId", "leaderId");
        assertThat(json.get("applicationId").asLong()).isEqualTo(applicationId);
        assertThat(json.get("applicantId").asLong()).isEqualTo(applicant.getId());
        assertThat(json.get("leaderId").asLong()).isEqualTo(leader.getId());
        assertThat(payload).doesNotContain(applicant.getEmail()).doesNotContain(MESSAGE);
    }

    @Test
    @DisplayName("[F-13][BC-06] 메시지 없이 신청해도 201이다")
    void applyWithoutMessage() {
        // when
        MvcTestResult result = apply(applicantSession, basecampId, null);

        // then
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(jdbc.queryForObject(
                        "SELECT message FROM basecamp_application WHERE applicant_id = ?",
                        String.class,
                        applicant.getId()))
                .isNull();
    }

    @Test
    @DisplayName("[F-13][TR-03] 본인확인하지 않은 회원이 신청하면 403 TRUST_LEVEL_INSUFFICIENT이고 신청 행과 이벤트를 남기지 않는다")
    void notIdentityVerifiedMemberIsForbidden() {
        // given
        Cookie session = fixture.verifiedSession(fixture.saveMember());

        // when
        MvcTestResult result = apply(session, basecampId, MESSAGE);

        // then
        assertFailure(result, HttpStatus.FORBIDDEN, "TRUST_LEVEL_INSUFFICIENT");
        assertNothingRecorded();
    }

    @Test
    @DisplayName("[F-13][BC-05] 최소 신뢰 단계 2 조건이면 단계 1인 회원은 403 BASECAMP_CONDITION_NOT_MET이다")
    void minTrustLevelTwoRejectsLevelOne() {
        // given
        jdbc.update("UPDATE basecamp SET min_trust_level = 2 WHERE id = ?", basecampId);

        // when
        MvcTestResult result = apply(applicantSession, basecampId, MESSAGE);

        // then
        assertFailure(result, HttpStatus.FORBIDDEN, "BASECAMP_CONDITION_NOT_MET");
        assertNothingRecorded();
    }

    @Test
    @DisplayName("[F-13][BC-05] 연령대 조건 밖인 본인확인 연령대는 403 BASECAMP_CONDITION_NOT_MET이고, 범위 안이면 신청된다")
    void ageGroupConditionIsChecked() {
        // given
        jdbc.update("UPDATE basecamp SET age_group_min = 30, age_group_max = 40 WHERE id = ?", basecampId);

        // when
        MvcTestResult outOfRange = apply(applicantSession, basecampId, MESSAGE);
        jdbc.update("UPDATE basecamp SET age_group_min = 20, age_group_max = 30 WHERE id = ?", basecampId);
        MvcTestResult inRange = apply(applicantSession, basecampId, MESSAGE);

        // then
        assertFailure(outOfRange, HttpStatus.FORBIDDEN, "BASECAMP_CONDITION_NOT_MET");
        assertThat(inRange).hasStatus(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("[F-13][BC-05] 동성만 받는 조건이면 본인확인한 성별이 다른 회원은 403 BASECAMP_CONDITION_NOT_MET이고, 같으면 신청된다")
    void sameGenderConditionIsChecked() {
        // given
        jdbc.update("UPDATE basecamp SET same_gender_only = TRUE, required_gender = 'FEMALE' WHERE id = ?", basecampId);
        Cookie femaleSession = fixture.identityVerifiedSession(fixture.saveMember(), "FEMALE");

        // when
        MvcTestResult male = apply(applicantSession, basecampId, MESSAGE);
        MvcTestResult female = apply(femaleSession, basecampId, MESSAGE);

        // then
        assertFailure(male, HttpStatus.FORBIDDEN, "BASECAMP_CONDITION_NOT_MET");
        assertThat(female).hasStatus(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("[F-13][BC-06] 같은 기간에 확정된 다른 베이스캠프의 멤버이면 409 BASECAMP_DATE_CONFLICT다")
    void overlappingConfirmedBasecampIsDateConflict() {
        // given
        Member otherLeader = fixture.saveMember();
        long confirmedId = fixture.insertBasecamp(otherLeader, spotId, "CONFIRMED", START_DATE.minusDays(1));
        fixture.insertMemberRow(confirmedId, applicant, "MEMBER", "ACTIVE");

        // when
        MvcTestResult result = apply(applicantSession, basecampId, MESSAGE);

        // then
        assertFailure(result, HttpStatus.CONFLICT, "BASECAMP_DATE_CONFLICT");
        assertNothingRecorded();
    }

    @Test
    @DisplayName("[F-13][BC-06] 확정된 베이스캠프의 종료일과 출발일이 하루만 겹쳐도 BASECAMP_DATE_CONFLICT이고, 하루 떨어지면 신청된다")
    void oneDayOverlapIsConflictButAdjacentDayIsNot() {
        // given
        Member otherLeader = fixture.saveMember();
        long confirmedId = fixture.insertBasecamp(otherLeader, spotId, "CONFIRMED", START_DATE.minusDays(2));
        fixture.insertMemberRow(confirmedId, applicant, "MEMBER", "ACTIVE");

        // when
        MvcTestResult overlapping = apply(applicantSession, basecampId, MESSAGE);
        jdbc.update(
                "UPDATE basecamp SET start_date = ?, end_date = ? WHERE id = ?",
                START_DATE.minusDays(2),
                START_DATE.minusDays(1),
                confirmedId);
        jdbc.update(
                "UPDATE basecamp SET start_date = ?, end_date = ? WHERE id = ?",
                START_DATE,
                START_DATE.plusDays(2),
                basecampId);
        MvcTestResult adjacent = apply(applicantSession, basecampId, MESSAGE);

        // then
        assertFailure(overlapping, HttpStatus.CONFLICT, "BASECAMP_DATE_CONFLICT");
        assertThat(adjacent).hasStatus(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("[F-13][BC-07] 거절된 신청 이력이나 탈퇴·강퇴된 멤버 행이 있으면 409 BASECAMP_REAPPLY_NOT_ALLOWED다")
    void rejectedLeftOrKickedMemberCannotReapply() {
        // given
        Member rejected = fixture.saveMember();
        Member left = fixture.saveMember();
        Member kicked = fixture.saveMember();
        Cookie rejectedSession = fixture.identityVerifiedSession(rejected, "MALE");
        Cookie leftSession = fixture.identityVerifiedSession(left, "MALE");
        Cookie kickedSession = fixture.identityVerifiedSession(kicked, "MALE");
        fixture.insertApplicationRow(basecampId, rejected, "REJECTED");
        fixture.insertMemberRow(basecampId, left, "MEMBER", "LEFT");
        fixture.insertMemberRow(basecampId, kicked, "MEMBER", "KICKED");

        // when
        MvcTestResult byRejected = apply(rejectedSession, basecampId, MESSAGE);
        MvcTestResult byLeft = apply(leftSession, basecampId, MESSAGE);
        MvcTestResult byKicked = apply(kickedSession, basecampId, MESSAGE);

        // then
        assertFailure(byRejected, HttpStatus.CONFLICT, "BASECAMP_REAPPLY_NOT_ALLOWED");
        assertFailure(byLeft, HttpStatus.CONFLICT, "BASECAMP_REAPPLY_NOT_ALLOWED");
        assertFailure(byKicked, HttpStatus.CONFLICT, "BASECAMP_REAPPLY_NOT_ALLOWED");
    }

    @Test
    @DisplayName("[F-13][BC-07] 이미 대기 중인 회원과 캠프 리더 본인은 409 BASECAMP_ALREADY_APPLIED다")
    void pendingApplicantAndLeaderAreAlreadyApplied() {
        // given
        Cookie leaderSession = fixture.identityVerifiedSession(leader, "FEMALE");
        assertThat(apply(applicantSession, basecampId, MESSAGE)).hasStatus(HttpStatus.CREATED);

        // when
        MvcTestResult again = apply(applicantSession, basecampId, MESSAGE);
        MvcTestResult byLeader = apply(leaderSession, basecampId, MESSAGE);

        // then
        assertFailure(again, HttpStatus.CONFLICT, "BASECAMP_ALREADY_APPLIED");
        assertFailure(byLeader, HttpStatus.CONFLICT, "BASECAMP_ALREADY_APPLIED");
        assertThat(count("basecamp_application")).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-13][BC-06] 모집 중이 아닌 베이스캠프(마감)에는 409 BASECAMP_INVALID_STATE다")
    void closedBasecampRejectsApplication() {
        // given
        jdbc.update("UPDATE basecamp SET status = 'CLOSED', closed_reason = 'LEADER' WHERE id = ?", basecampId);

        // when
        MvcTestResult result = apply(applicantSession, basecampId, MESSAGE);

        // then
        assertFailure(result, HttpStatus.CONFLICT, "BASECAMP_INVALID_STATE");
        assertNothingRecorded();
    }

    @Test
    @DisplayName("[F-13][BC-07] 대기 신청이 20건이면 409 BASECAMP_PENDING_LIMIT이고, 19건이면 신청된다")
    void pendingLimitIsTwenty() {
        // given
        for (int i = 0; i < 19; i++) {
            fixture.insertApplicationRow(basecampId, fixture.saveMember(), "PENDING");
        }
        Cookie secondSession = fixture.identityVerifiedSession(fixture.saveMember(), "MALE");

        // when
        MvcTestResult twentieth = apply(applicantSession, basecampId, MESSAGE);
        MvcTestResult twentyFirst = apply(secondSession, basecampId, MESSAGE);

        // then
        assertThat(twentieth).hasStatus(HttpStatus.CREATED);
        assertFailure(twentyFirst, HttpStatus.CONFLICT, "BASECAMP_PENDING_LIMIT");
        assertThat(count("basecamp_application")).isEqualTo(20);
        assertThat(count("outbox_event")).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-13] 없는 베이스캠프에 신청하면 404 NOT_FOUND다")
    void unknownBasecampIsNotFound() {
        // when
        MvcTestResult result = apply(applicantSession, basecampId + 1000, MESSAGE);

        // then
        assertFailure(result, HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    @Test
    @DisplayName("[F-13] 신청 메시지가 501자이면 400 INVALID_INPUT이고 신청 행과 이벤트를 남기지 않는다")
    void tooLongMessageIsInvalidInput() {
        // when
        MvcTestResult result = apply(applicantSession, basecampId, "가".repeat(501));

        // then
        assertFailure(result, HttpStatus.BAD_REQUEST, "INVALID_INPUT");
        assertNothingRecorded();
    }

    @Test
    @DisplayName("[F-13][NFR-04] 신청이 거부되면 트랜잭션이 롤백되어 outbox_event 행이 남지 않는다")
    void rejectedApplicationLeavesNoOutboxEvent() {
        // given
        assertThat(apply(applicantSession, basecampId, MESSAGE)).hasStatus(HttpStatus.CREATED);

        // when
        MvcTestResult again = apply(applicantSession, basecampId, MESSAGE);

        // then
        assertFailure(again, HttpStatus.CONFLICT, "BASECAMP_ALREADY_APPLIED");
        assertThat(count("outbox_event")).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-13][BC-06] 신청을 취소하면 204이고, 다시 신청하면 같은 신청 ID로 PENDING이 되며 이벤트를 새로 기록한다")
    void cancelThenApplyAgainReusesApplicationRow() {
        // given
        long applicationId = applicationIdOf(apply(applicantSession, basecampId, MESSAGE));

        // when
        MvcTestResult canceled = cancel(applicantSession, basecampId);
        String statusAfterCancel = statusOf(applicationId);
        MvcTestResult reapplied = apply(applicantSession, basecampId, "다시 신청해요");

        // then
        assertThat(canceled).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(statusAfterCancel).isEqualTo("CANCELED");
        assertThat(reapplied).hasStatus(HttpStatus.CREATED);
        assertThat(applicationIdOf(reapplied)).isEqualTo(applicationId);
        assertThat(statusOf(applicationId)).isEqualTo("PENDING");
        assertThat(count("basecamp_application")).isEqualTo(1);
        assertThat(count("outbox_event")).isEqualTo(2);
    }

    @Test
    @DisplayName("[F-13][BC-06] 마감된 베이스캠프에서도 대기 중인 신청을 취소할 수 있다")
    void cancelWorksWhileClosed() {
        // given
        long applicationId = applicationIdOf(apply(applicantSession, basecampId, MESSAGE));
        jdbc.update("UPDATE basecamp SET status = 'CLOSED', closed_reason = 'LEADER' WHERE id = ?", basecampId);

        // when
        MvcTestResult result = cancel(applicantSession, basecampId);

        // then
        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(statusOf(applicationId)).isEqualTo("CANCELED");
    }

    @Test
    @DisplayName("[F-13][BC-06] 신청이 없는 회원이 취소하면 404 NOT_FOUND, 이미 승인된 신청을 취소하면 409 BASECAMP_INVALID_STATE다")
    void cancelWithoutApplicationOrApproved() {
        // given
        Member approved = fixture.saveMember();
        Cookie approvedSession = fixture.identityVerifiedSession(approved, "MALE");
        fixture.insertApplicationRow(basecampId, approved, "APPROVED");

        // when
        MvcTestResult withoutApplication = cancel(applicantSession, basecampId);
        MvcTestResult approvedApplication = cancel(approvedSession, basecampId);

        // then
        assertFailure(withoutApplication, HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertFailure(approvedApplication, HttpStatus.CONFLICT, "BASECAMP_INVALID_STATE");
    }

    @Test
    @DisplayName("[F-13] 본인확인하지 않은 회원이 취소하면 403 TRUST_LEVEL_INSUFFICIENT다")
    void cancelRequiresIdentityVerification() {
        // given
        Cookie session = fixture.verifiedSession(fixture.saveMember());

        // when
        MvcTestResult result = cancel(session, basecampId);

        // then
        assertFailure(result, HttpStatus.FORBIDDEN, "TRUST_LEVEL_INSUFFICIENT");
    }

    private MvcTestResult apply(Cookie session, long id, String message) {
        String body = message == null ? "{}" : "{\"message\":\"%s\"}".formatted(message);
        return mvc.post()
                .uri("/api/basecamps/" + id + "/applications")
                .with(csrf())
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult cancel(Cookie session, long id) {
        return mvc.delete()
                .uri("/api/basecamps/" + id + "/applications/me")
                .with(csrf())
                .cookie(session)
                .exchange();
    }

    private static long applicationIdOf(MvcTestResult created) {
        AtomicLong applicationId = new AtomicLong();
        assertThat(created)
                .bodyJson()
                .extractingPath("$.applicationId")
                .asNumber()
                .satisfies(number -> applicationId.set(number.longValue()));
        return applicationId.get();
    }

    private static void assertFailure(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
    }

    private void assertNothingRecorded() {
        assertThat(count("basecamp_application")).isZero();
        assertThat(count("outbox_event")).isZero();
    }

    private String statusOf(long applicationId) {
        return jdbc.queryForObject("SELECT status FROM basecamp_application WHERE id = ?", String.class, applicationId);
    }

    private int count(String table) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }
}
