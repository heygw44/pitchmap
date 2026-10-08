package com.pitchmap.admin.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.notification.application.OutboxPublisher;
import com.pitchmap.trust.application.CompanionReviewFixture;
import com.pitchmap.trust.application.CompanionReviewQueryService;
import com.pitchmap.trust.application.MemberReportCommand;
import com.pitchmap.trust.application.MemberReportFixture;
import com.pitchmap.trust.application.MemberReportService;
import com.pitchmap.trust.application.SanctionConfirmCommand;
import com.pitchmap.trust.application.SanctionConfirmService;
import com.pitchmap.trust.domain.ReportKind;
import com.pitchmap.trust.domain.ReportType;
import com.pitchmap.trust.domain.SanctionType;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
class AdminReportSanctionApiIntegrationTest {

    private static final String REPORTS = "/api/admin/member-reports";
    private static final String SANCTIONS = "/api/admin/sanctions";
    private static final String AUDIT_LOGS = "/api/admin/audit-logs";
    private static final String PASSWORD = "Valid-pass1";
    private static final String SESSION_COOKIE = "SESSION";
    private static final String SECRET_REASON = "비밀사유가들어간문장";
    private static final String SECRET_NOTE = "비밀메모가들어간문장";
    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private MemberReportService memberReportService;

    @Autowired
    private SanctionConfirmService sanctionConfirmService;

    @Autowired
    private CompanionReviewQueryService companionReviewQueryService;

    @Autowired
    private OutboxPublisher outboxPublisher;

    private CompanionReviewFixture fixture;
    private MemberReportFixture reportFixture;
    private long adminId;
    private Cookie adminSession;
    private long basecampId;
    private long reporterId;
    private long targetId;

    @BeforeEach
    void setUp() {
        fixture = new CompanionReviewFixture(jdbc, memberRepository);
        reportFixture = new MemberReportFixture(jdbc);
        adminId = fixture.saveVerifiedMember(TestSequence.nickname());
        jdbc.update("UPDATE member SET role = 'ADMIN' WHERE id = ?", adminId);
        adminSession = login(adminId);
        basecampId = fixture.saveBasecamp("COMPLETED", BASE);
        reporterId = fixture.saveVerifiedMember(TestSequence.nickname());
        targetId = fixture.saveVerifiedMember(TestSequence.nickname());
    }

    @Test
    @DisplayName("[F-21][SF-02] 신고 목록은 긴급 신고, 금전 요구 신고, 나머지 순이고 같은 묶음에서는 오래된 신고가 먼저다")
    void listIsOrderedByUrgencyMoneyThenAge() {
        long normal1 = insertReport("NO_SHOW", "RECEIVED", BASE);
        long money1 = insertReport("MONEY_REQUEST", "RECEIVED", BASE.plus(Duration.ofHours(1)));
        long urgent1 = insertReport("HARASSMENT_OR_THREAT", "RECEIVED", BASE.plus(Duration.ofHours(2)));
        long normal2 = insertReport("OFFENSIVE_BEHAVIOR", "RECEIVED", BASE.plus(Duration.ofHours(3)));
        long urgent2 = insertReport("HARASSMENT_OR_THREAT", "RECEIVED", BASE.plus(Duration.ofHours(4)));
        long money2 = insertReport("MONEY_REQUEST", "RECEIVED", BASE.plus(Duration.ofHours(5)));

        MvcTestResult result = get(REPORTS);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(ids(result, "$.content[*].reportId"))
                .containsExactly(urgent1, urgent2, money1, money2, normal1, normal2);
        assertThat(result).bodyJson().extractingPath("$.content[0].kind").isEqualTo("MEMBER");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].reporter.memberId")
                .isNotNull();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].target.nickname")
                .isNotNull();
        assertThat(result).bodyJson().doesNotHavePath("$.content[0].content");
        assertThat(result).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("[F-21] 신고 목록은 status와 urgent 필터, 페이지를 적용하고 알 수 없는 status는 400이다")
    void listFiltersAndPages() {
        long received = insertReport("NO_SHOW", "RECEIVED", BASE);
        long inReview = insertReport("MONEY_REQUEST", "IN_REVIEW", BASE.plus(Duration.ofHours(1)));
        long urgent = insertReport("HARASSMENT_OR_THREAT", "IN_REVIEW", BASE.plus(Duration.ofHours(2)));

        assertThat(ids(get(REPORTS + "?status=IN_REVIEW"), "$.content[*].reportId"))
                .containsExactly(urgent, inReview);
        assertThat(ids(get(REPORTS + "?urgent=true"), "$.content[*].reportId")).containsExactly(urgent);
        assertThat(ids(get(REPORTS + "?urgent=false"), "$.content[*].reportId")).containsExactly(inReview, received);
        assertThat(ids(get(REPORTS + "?status=ACTIONED"), "$.content[*].reportId"))
                .isEmpty();
        MvcTestResult firstPage = get(REPORTS + "?size=2&page=0");
        MvcTestResult secondPage = get(REPORTS + "?size=2&page=1");
        assertThat(ids(firstPage, "$.content[*].reportId")).containsExactly(urgent, inReview);
        assertThat(firstPage).bodyJson().extractingPath("$.hasNext").isEqualTo(true);
        assertThat(ids(secondPage, "$.content[*].reportId")).containsExactly(received);
        assertThat(secondPage).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        MvcTestResult invalid = get(REPORTS + "?status=NOPE");
        assertThat(invalid).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(invalid).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
    }

    @Test
    @DisplayName("[F-21] 관리자가 아닌 회원은 관리자 API가 403이고, 세션이 없으면 401이다")
    void nonAdminCannotUseAdminApis() {
        Cookie userSession = login(reporterId);

        MvcTestResult forbidden = mvc.get().uri(REPORTS).cookie(userSession).exchange();
        MvcTestResult unauthorized = mvc.get().uri(AUDIT_LOGS).exchange();
        MvcTestResult forbiddenPost = mvc.post()
                .uri(SANCTIONS + "/1/lift")
                .cookie(userSession)
                .with(csrf())
                .exchange();

        assertThat(forbidden).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(forbiddenPost).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(unauthorized).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("[F-21][SN-05] 신고 상세는 기각된 신고의 임시 정지를 제재 이력에서 빼고, 조치한 신고의 임시 정지는 보여 준다")
    void detailHidesTemporarySuspensionOfDismissedReport() {
        long dismissedTarget = fixture.saveVerifiedMember(TestSequence.nickname());
        long dismissedReport = urgentReport(dismissedTarget);
        post(REPORTS + "/" + dismissedReport + "/start-review", null);
        post(REPORTS + "/" + dismissedReport + "/dismiss", null);
        long actionedReport = urgentReport(dismissedTarget);
        post(REPORTS + "/" + actionedReport + "/start-review", null);
        sanctionConfirmService.confirm(
                new SanctionConfirmCommand(dismissedTarget, null, SanctionType.WARNING, "욕설", adminId));

        MvcTestResult dismissedDetail = get(REPORTS + "/" + dismissedReport);
        MvcTestResult actionedDetail = get(REPORTS + "/" + actionedReport);

        assertThat(dismissedDetail).hasStatus(HttpStatus.OK);
        List<String> types = json(dismissedDetail, "$.sanctionHistory[*].type");
        assertThat(types).containsExactly("WARNING", "TEMPORARY_72H");
        List<String> statuses = json(dismissedDetail, "$.sanctionHistory[*].status");
        assertThat(statuses).containsExactly("ACTIVE", "ACTIVE");
        assertThat(actionedDetail).bodyJson().extractingPath("$.status").isEqualTo("IN_REVIEW");
        assertThat(dismissedDetail).bodyJson().extractingPath("$.status").isEqualTo("DISMISSED");
    }

    @Test
    @DisplayName("[F-21][SN-05] 기각된 임시 정지는 LIFTED가 되고 제재 이력의 임시 정지 항목이 없다")
    void dismissedTemporarySuspensionIsNotInHistory() {
        long dismissedTarget = fixture.saveVerifiedMember(TestSequence.nickname());
        long reportId = urgentReport(dismissedTarget);
        post(REPORTS + "/" + reportId + "/start-review", null);
        post(REPORTS + "/" + reportId + "/dismiss", null);

        MvcTestResult detail = get(REPORTS + "/" + reportId);

        assertThat(sanctionStatuses(dismissedTarget)).containsExactly("LIFTED");
        assertThat(detail).hasStatus(HttpStatus.OK);
        assertThat((List<?>) json(detail, "$.sanctionHistory")).isEmpty();
    }

    @Test
    @DisplayName("[F-21] 신고 상세는 신고 내용, 베이스캠프, 신고자와 대상을 담고 회원 신고이면 companionReview가 null이다")
    void detailOfMemberReport() {
        long reportId = insertReport("NO_SHOW", "RECEIVED", BASE);

        MvcTestResult result = get(REPORTS + "/" + reportId);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.reportId").isEqualTo((int) reportId);
        assertThat(result).bodyJson().extractingPath("$.content").isEqualTo("신고 내용 NO_SHOW");
        assertThat(result).bodyJson().extractingPath("$.basecamp.title").isEqualTo("굴업도 주말 1박");
        assertThat(result).bodyJson().extractingPath("$.basecamp.status").isEqualTo("COMPLETED");
        assertThat(result).bodyJson().extractingPath("$.basecamp.startDate").isEqualTo("2026-09-20");
        assertThat(result).bodyJson().extractingPath("$.companionReview").isNull();
        assertThat(result).bodyJson().extractingPath("$.handledBy").isNull();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.sanctionHistory")
                .asList()
                .isEmpty();
    }

    @Test
    @DisplayName("[F-21][SN-07] 후기 신고 상세는 신고된 후기의 코멘트, 태그, 다시 동행 여부, 숨김 여부를 담는다")
    void detailOfReviewReport() {
        long reviewId = fixture.insertReview(basecampId, targetId, reporterId, true, BASE);
        fixture.insertTag(reviewId, "LATE");
        fixture.insertTag(reviewId, "CONSIDERATE");
        long reportId = reportFixture.insertReviewReport(reporterId, targetId, basecampId, reviewId, "IN_REVIEW", BASE);

        MvcTestResult result = get(REPORTS + "/" + reportId);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.kind").isEqualTo("REVIEW");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.companionReview.comment")
                .isEqualTo("코멘트 " + targetId + "→" + reporterId);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.companionReview.tags")
                .asList()
                .containsExactly("CONSIDERATE", "LATE");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.companionReview.rejoinWanted")
                .isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.companionReview.hidden").isEqualTo(false);
    }

    @Test
    @DisplayName("[F-21] 없는 신고는 상세와 모든 처리가 404 NOT_FOUND이다")
    void unknownReportIsNotFound() {
        for (MvcTestResult result : List.of(
                get(REPORTS + "/999999"),
                post(REPORTS + "/999999/start-review", null),
                post(REPORTS + "/999999/action", "{\"hideReview\":true}"),
                post(REPORTS + "/999999/dismiss", null),
                post(SANCTIONS + "/999999/lift", null))) {
            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        }
        assertThat(count("admin_audit_log")).isZero();
    }

    @Test
    @DisplayName("[SN-04] 접수 상태에서는 조치와 기각이 409이고, 검토 시작은 한 번만 되며, 처리한 신고는 다시 처리할 수 없다")
    void stateTransitionsAreEnforced() {
        long reportId = insertReport("NO_SHOW", "RECEIVED", BASE);
        String path = REPORTS + "/" + reportId;

        assertThat(post(path + "/action", "{\"hideReview\":true,\"sanction\":null}"))
                .hasStatus(HttpStatus.CONFLICT);
        assertThat(post(path + "/dismiss", null)).hasStatus(HttpStatus.CONFLICT);
        MvcTestResult started = post(path + "/start-review", null);
        assertThat(started).hasStatus(HttpStatus.OK);
        assertThat(started).bodyJson().extractingPath("$.status").isEqualTo("IN_REVIEW");
        MvcTestResult startedTwice = post(path + "/start-review", null);
        assertThat(startedTwice).hasStatus(HttpStatus.CONFLICT);
        assertThat(startedTwice).bodyJson().extractingPath("$.code").isEqualTo("REPORT_INVALID_STATE");
        assertThat(post(path + "/dismiss", "{\"note\":\"근거 없음\"}")).hasStatus(HttpStatus.OK);
        assertThat(post(path + "/dismiss", null)).hasStatus(HttpStatus.CONFLICT);
        assertThat(post(path + "/action", "{\"sanction\":{\"type\":\"WARNING\",\"reason\":\"사유\"}}"))
                .hasStatus(HttpStatus.CONFLICT);
        assertThat(post(path + "/start-review", null)).hasStatus(HttpStatus.CONFLICT);

        assertThat(reportStatus(reportId)).isEqualTo("DISMISSED");
        assertThat(count("sanction")).isZero();
        assertThat(count("admin_audit_log")).isEqualTo(2);
    }

    @Test
    @DisplayName("[SN-04][SN-12] 정지를 조치하면 신고가 ACTIONED가 되고 대상의 기존 세션이 401이며 베이스캠프 정리와 결과 이벤트가 기록된다")
    void actionWithSuspensionInvalidatesSessionAndRecords() {
        Cookie session = login(targetId);
        Cookie secondSession = login(targetId);
        long ledBasecamp = ledBasecamp(targetId);
        long reportId = insertReport("NO_SHOW", "RECEIVED", BASE);
        post(REPORTS + "/" + reportId + "/start-review", null);

        MvcTestResult result = post(
                REPORTS + "/" + reportId + "/action",
                "{\"sanction\":{\"type\":\"PERMANENT\",\"reason\":\"" + SECRET_REASON + "\"},\"hideReview\":false,"
                        + "\"note\":\"" + SECRET_NOTE + "\"}");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("ACTIONED");
        long sanctionId = ((Number) json(result, "$.sanctionId")).longValue();
        assertThat(mvc.get().uri("/api/me").cookie(session).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/me").cookie(secondSession).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(sessionCount(targetId)).isZero();
        assertThat(memberStatus(targetId)).isEqualTo("SUSPENDED");
        assertThat(jdbc.queryForObject(
                        "SELECT CONCAT(status, ':', handled_by, ':', result_note) FROM member_report WHERE id = ?",
                        String.class,
                        reportId))
                .isEqualTo("ACTIONED:" + adminId + ":" + SECRET_NOTE);
        assertThat(jdbc.queryForObject(
                        "SELECT CONCAT(type, ':', level, ':', report_id, ':', created_by, ':', status)"
                                + " FROM sanction WHERE id = ?",
                        String.class,
                        sanctionId))
                .isEqualTo("PERMANENT:4:" + reportId + ":" + adminId + ":ACTIVE");
        assertThat(count("outbox_event WHERE event_type = 'SANCTION_BASECAMP_CLEANUP'"))
                .isEqualTo(1);
        Map<String, Object> payload = resolvedPayload();
        assertThat(payload).containsOnlyKeys("reportId", "reporterId", "result");
        assertThat(payload.get("result")).isEqualTo("ACTIONED");
        assertThat(((Number) payload.get("reportId")).longValue()).isEqualTo(reportId);
        assertThat(((Number) payload.get("reporterId")).longValue())
                .isEqualTo(jdbc.queryForObject(
                        "SELECT reporter_id FROM member_report WHERE id = ?", Long.class, reportId));
        assertAuditRow("REPORT_ACTION", reportId);
        assertThat(count("admin_audit_log WHERE action = 'REPORT_ACTION'")).isEqualTo(1);
        assertThat(allAuditDetails()).doesNotContain(SECRET_REASON, SECRET_NOTE);

        outboxPublisher.publishPending();

        assertThat(basecampStatus(ledBasecamp)).isEqualTo("CANCELED");
    }

    @Test
    @DisplayName("[SN-10] 경고를 조치하면 대상의 세션이 유지되고 이용 정지 이벤트는 기록하지 않는다")
    void actionWithWarningKeepsSession() {
        Cookie session = login(targetId);
        long reportId = insertReport("OFFENSIVE_BEHAVIOR", "IN_REVIEW", BASE);

        MvcTestResult result = post(
                REPORTS + "/" + reportId + "/action",
                "{\"sanction\":{\"type\":\"WARNING\",\"reason\":\"욕설\"},\"hideReview\":false}");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(mvc.get().uri("/api/me").cookie(session).exchange()).hasStatus(HttpStatus.OK);
        assertThat(sanctionStatuses(targetId)).containsExactly("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT level FROM sanction WHERE member_id = ?", Integer.class, targetId))
                .isEqualTo(1);
        assertThat(count("outbox_event WHERE event_type = 'SANCTION_BASECAMP_CLEANUP'"))
                .isZero();
        assertThat(count("outbox_event WHERE event_type = 'MEMBER_REPORT_RESOLVED'"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("[SN-10] 계산된 다음 단계와 다른 제재를 요청하면 400이고 신고 상태, 제재, 이벤트, 감사 로그가 그대로다")
    void levelMismatchChangesNothing() {
        long reportId = insertReport("NO_SHOW", "IN_REVIEW", BASE);

        MvcTestResult result = post(
                REPORTS + "/" + reportId + "/action", "{\"sanction\":{\"type\":\"SUSPEND_7D\",\"reason\":\"단계 건너뜀\"}}");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(reportStatus(reportId)).isEqualTo("IN_REVIEW");
        assertThat(count("sanction")).isZero();
        assertThat(count("outbox_event")).isZero();
        assertThat(count("admin_audit_log")).isZero();
    }

    @Test
    @DisplayName("[SN-04] 제재도 후기 숨김도 없거나, 회원 신고에 후기 숨김을 요청하거나, 제재 종류가 올바르지 않으면 400이다")
    void invalidActionRequests() {
        long reportId = insertReport("NO_SHOW", "IN_REVIEW", BASE);
        String path = REPORTS + "/" + reportId + "/action";

        for (String body : List.of(
                "{\"sanction\":null,\"hideReview\":false}",
                "{}",
                "{\"hideReview\":true}",
                "{\"sanction\":{\"type\":\"TEMPORARY_72H\",\"reason\":\"사유\"}}",
                "{\"sanction\":{\"type\":\"NOPE\",\"reason\":\"사유\"}}")) {
            MvcTestResult result = post(path, body);

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
        assertThat(reportStatus(reportId)).isEqualTo("IN_REVIEW");
        assertThat(count("sanction")).isZero();
        assertThat(count("outbox_event")).isZero();
    }

    @Test
    @DisplayName("[SN-04] 요청 형식 오류는 상태 검사보다 먼저이고, 상태 오류는 요청 내용 검사보다 먼저다")
    void validationOrder() {
        long received = insertReport("NO_SHOW", "RECEIVED", BASE);
        long other = insertReport("MONEY_REQUEST", "RECEIVED", BASE);

        MvcTestResult unknownType =
                post(REPORTS + "/" + received + "/action", "{\"sanction\":{\"type\":\"NOPE\",\"reason\":\"사유\"}}");
        MvcTestResult emptyRequestOnWrongState = post(REPORTS + "/" + other + "/action", "{\"hideReview\":false}");

        assertThat(unknownType).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(emptyRequestOnWrongState).hasStatus(HttpStatus.CONFLICT);
        assertThat(emptyRequestOnWrongState).bodyJson().extractingPath("$.code").isEqualTo("REPORT_INVALID_STATE");
    }

    @Test
    @DisplayName("[SN-07] 후기 신고를 조치하며 후기를 숨기면 hidden_at이 채워지고 받은 후기 목록에서 빠진다")
    void hideReviewRemovesItFromReceivedList() {
        long reviewId = fixture.insertReview(basecampId, targetId, reporterId, true, BASE);
        long reportId = reportFixture.insertReviewReport(reporterId, targetId, basecampId, reviewId, "IN_REVIEW", BASE);
        assertThat(companionReviewQueryService.received(reporterId, 0, 20).content())
                .hasSize(1);

        MvcTestResult result = post(REPORTS + "/" + reportId + "/action", "{\"sanction\":null,\"hideReview\":true}");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.sanctionId").isNull();
        assertThat(jdbc.queryForObject(
                        "SELECT hidden_at IS NOT NULL FROM companion_review WHERE id = ?", Boolean.class, reviewId))
                .isTrue();
        assertThat(companionReviewQueryService.received(reporterId, 0, 20).content())
                .isEmpty();
        assertThat(count("sanction")).isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT detail FROM admin_audit_log WHERE action = 'REPORT_ACTION'", String.class))
                .contains("\"hideReview\": true")
                .doesNotContain("sanctionId");
    }

    @Test
    @DisplayName("[SN-07] 후기 신고를 조치하며 후기 숨김과 경고를 함께 요청하면 둘 다 적용한다")
    void hideReviewAndWarningTogether() {
        long reviewId = fixture.insertReview(basecampId, targetId, reporterId, false, BASE);
        long reportId = reportFixture.insertReviewReport(reporterId, targetId, basecampId, reviewId, "IN_REVIEW", BASE);

        MvcTestResult result = post(
                REPORTS + "/" + reportId + "/action",
                "{\"sanction\":{\"type\":\"WARNING\",\"reason\":\"부적절한 후기\"},\"hideReview\":true}");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(sanctionStatuses(targetId)).containsExactly("ACTIVE");
        assertThat(jdbc.queryForObject(
                        "SELECT hidden_at IS NOT NULL FROM companion_review WHERE id = ?", Boolean.class, reviewId))
                .isTrue();
    }

    @Test
    @DisplayName("[SN-05] 긴급 신고를 기각하면 임시 정지가 LIFTED가 되고 미인증 회원은 UNVERIFIED로 돌아가며 다음 제재 단계는 1단계다")
    void dismissUrgentReportReleasesUnverifiedMember() {
        long reportId = urgentReport(targetId);
        assertThat(memberStatus(targetId)).isEqualTo("SUSPENDED");
        post(REPORTS + "/" + reportId + "/start-review", null);

        MvcTestResult result = post(REPORTS + "/" + reportId + "/dismiss", "{\"note\":\"오해였음\"}");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("DISMISSED");
        assertThat(sanctionStatuses(targetId)).containsExactly("LIFTED");
        assertThat(jdbc.queryForObject("SELECT lifted_by FROM sanction WHERE member_id = ?", Long.class, targetId))
                .isEqualTo(adminId);
        assertThat(memberStatus(targetId)).isEqualTo("UNVERIFIED");
        assertThat(jdbc.queryForObject("SELECT suspended_until FROM member WHERE id = ?", Object.class, targetId))
                .isNull();
        assertThat(resolvedPayload().get("result")).isEqualTo("DISMISSED");
        assertAuditRow("REPORT_DISMISS", reportId);
        assertThat(jdbc.queryForObject(
                        "SELECT detail FROM admin_audit_log WHERE action = 'REPORT_DISMISS'", String.class))
                .contains("liftedSanctionId");
        sanctionConfirmService.confirm(new SanctionConfirmCommand(targetId, null, SanctionType.WARNING, "경고", adminId));
        assertThat(jdbc.queryForObject("SELECT level FROM sanction WHERE type = 'WARNING'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("[SN-05] 이메일 인증을 마친 회원은 긴급 신고가 기각되면 ACTIVE로 돌아간다")
    void dismissUrgentReportReleasesVerifiedMemberToActive() {
        jdbc.update("UPDATE member SET email_verified_at = NOW(6), status = 'ACTIVE' WHERE id = ?", targetId);
        long reportId = urgentReport(targetId);
        post(REPORTS + "/" + reportId + "/start-review", null);

        post(REPORTS + "/" + reportId + "/dismiss", null);

        assertThat(memberStatus(targetId)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("[SN-05] 같은 대상의 긴급 신고 2건 중 나중 신고를 기각하면 정지는 유지되고 종료 시각은 남은 임시 정지로 줄어든다")
    void dismissOneOfTwoUrgentReportsKeepsSuspension() {
        long first = urgentReport(targetId);
        Instant firstEnd = sanctionEnd(first);
        clock.advance(Duration.ofHours(1));
        long second = urgentReport(targetId);
        assertThat(sanctionEnd(second)).isAfter(firstEnd);
        post(REPORTS + "/" + second + "/start-review", null);

        post(REPORTS + "/" + second + "/dismiss", null);

        assertThat(memberStatus(targetId)).isEqualTo("SUSPENDED");
        assertThat(suspendedUntil(targetId)).isEqualTo(firstEnd);
        assertThat(sanctionStatus(sanctionOfReport(first))).isEqualTo("ACTIVE");
        assertThat(sanctionStatus(sanctionOfReport(second))).isEqualTo("LIFTED");
    }

    @Test
    @DisplayName("[SN-05] 확정된 영구 정지가 남은 회원은 긴급 신고를 기각해도 정지를 유지한다")
    void dismissKeepsConfirmedSuspension() {
        sanctionConfirmService.confirm(
                new SanctionConfirmCommand(targetId, null, SanctionType.PERMANENT, "심각한 위반", adminId));
        long reportId = urgentReport(targetId);
        post(REPORTS + "/" + reportId + "/start-review", null);

        post(REPORTS + "/" + reportId + "/dismiss", null);

        assertThat(memberStatus(targetId)).isEqualTo("SUSPENDED");
        assertThat(suspendedUntil(targetId)).isNull();
        assertThat(sanctionStatus(sanctionOfReport(reportId))).isEqualTo("LIFTED");
    }

    @Test
    @DisplayName("[SN-05] 긴급이 아닌 신고를 기각하면 대상 회원의 상태를 건드리지 않는다")
    void dismissNonUrgentReportLeavesMemberAlone() {
        sanctionConfirmService.confirm(
                new SanctionConfirmCommand(targetId, null, SanctionType.PERMANENT, "반복", adminId));
        String statusBefore = memberStatus(targetId);
        long reportId = insertReport("NO_SHOW", "IN_REVIEW", BASE);

        post(REPORTS + "/" + reportId + "/dismiss", null);

        assertThat(memberStatus(targetId)).isEqualTo(statusBefore);
        assertThat(jdbc.queryForObject(
                        "SELECT detail FROM admin_audit_log WHERE action = 'REPORT_DISMISS'", String.class))
                .doesNotContain("liftedSanctionId");
    }

    @Test
    @DisplayName("[SN-05] 긴급 신고를 조치하면 그 신고의 임시 정지는 해제하지 않고 그대로 둔다")
    void actionLeavesTemporarySuspensionAlone() {
        long reportId = urgentReport(targetId);
        post(REPORTS + "/" + reportId + "/start-review", null);

        MvcTestResult result = post(
                REPORTS + "/" + reportId + "/action", "{\"sanction\":{\"type\":\"WARNING\",\"reason\":\"위협 발언\"}}");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM sanction WHERE report_id = ? AND type = 'TEMPORARY_72H'",
                        String.class,
                        reportId))
                .isEqualTo("ACTIVE");
        assertThat(memberStatus(targetId)).isEqualTo("SUSPENDED");
    }

    @Test
    @DisplayName("[SN-15] 7일 정지를 해제하면 로그인할 수 있고 다음 제재 단계는 해제 전 단계로 돌아간다")
    void liftSuspensionAllowsLoginAndRestoresNextLevel() {
        sanctionConfirmService.confirm(new SanctionConfirmCommand(targetId, null, SanctionType.WARNING, "경고", adminId));
        long sanctionId = confirm(SanctionType.SUSPEND_7D);
        assertThat(loginRequest(targetId)).hasStatus(HttpStatus.FORBIDDEN);

        MvcTestResult result = post(SANCTIONS + "/" + sanctionId + "/lift", null);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("LIFTED");
        assertThat(result).bodyJson().extractingPath("$.sanctionId").isEqualTo((int) sanctionId);
        assertThat(memberStatus(targetId)).isEqualTo("UNVERIFIED");
        assertThat(loginRequest(targetId)).hasStatus(HttpStatus.OK);
        assertThat(jdbc.queryForObject(
                        "SELECT CONCAT(status, ':', lifted_by) FROM sanction WHERE id = ?", String.class, sanctionId))
                .isEqualTo("LIFTED:" + adminId);
        assertThat(confirm(SanctionType.SUSPEND_7D)).isNotEqualTo(sanctionId);
        assertThat(jdbc.queryForObject(
                        "SELECT detail FROM admin_audit_log WHERE action = 'SANCTION_LIFT'", String.class))
                .contains("\"sanctionType\": \"SUSPEND_7D\"")
                .contains("\"toStatus\": \"LIFTED\"");
        assertThat(jdbc.queryForObject(
                        "SELECT target_id FROM admin_audit_log WHERE action = 'SANCTION_LIFT'", Long.class))
                .isEqualTo(sanctionId);
    }

    @Test
    @DisplayName("[SN-15] 영구 정지를 해제하면 로그인할 수 있다")
    void liftPermanentSuspension() {
        long sanctionId = confirm(SanctionType.PERMANENT);

        assertThat(post(SANCTIONS + "/" + sanctionId + "/lift", null)).hasStatus(HttpStatus.OK);

        assertThat(memberStatus(targetId)).isEqualTo("UNVERIFIED");
        assertThat(loginRequest(targetId)).hasStatus(HttpStatus.OK);
    }

    @Test
    @DisplayName("[SN-15] 경고를 해제해도 회원 상태는 그대로이고 다음 제재는 다시 경고부터다")
    void liftWarning() {
        long sanctionId = confirm(SanctionType.WARNING);

        assertThat(post(SANCTIONS + "/" + sanctionId + "/lift", null)).hasStatus(HttpStatus.OK);

        assertThat(memberStatus(targetId)).isEqualTo("UNVERIFIED");
        assertThat(confirm(SanctionType.WARNING)).isNotEqualTo(sanctionId);
    }

    @Test
    @DisplayName("[SN-15] 두 정지 중 더 늦게 끝나는 정지를 해제하면 종료 시각이 남은 정지로 줄어든다")
    void liftLongerSuspensionShortensUntil() {
        confirm(SanctionType.WARNING);
        confirm(SanctionType.SUSPEND_7D);
        Instant sevenDaysEnd = suspendedUntil(targetId);
        long thirtyDays = confirm(SanctionType.SUSPEND_30D);
        assertThat(suspendedUntil(targetId)).isAfter(sevenDaysEnd);

        post(SANCTIONS + "/" + thirtyDays + "/lift", null);

        assertThat(memberStatus(targetId)).isEqualTo("SUSPENDED");
        assertThat(suspendedUntil(targetId)).isEqualTo(sevenDaysEnd);
    }

    @Test
    @DisplayName("[SN-15] 이미 해제됐거나 기간이 끝난 제재를 해제하면 409 SANCTION_INVALID_STATE이다")
    void liftInactiveSanctionIsConflict() {
        long lifted = confirm(SanctionType.WARNING);
        post(SANCTIONS + "/" + lifted + "/lift", null);
        long expired = confirm(SanctionType.WARNING);
        jdbc.update("UPDATE sanction SET status = 'EXPIRED' WHERE id = ?", expired);

        MvcTestResult liftedAgain = post(SANCTIONS + "/" + lifted + "/lift", null);
        MvcTestResult liftExpired = post(SANCTIONS + "/" + expired + "/lift", null);

        assertThat(liftedAgain).hasStatus(HttpStatus.CONFLICT);
        assertThat(liftedAgain).bodyJson().extractingPath("$.code").isEqualTo("SANCTION_INVALID_STATE");
        assertThat(liftExpired).hasStatus(HttpStatus.CONFLICT);
        assertThat(count("admin_audit_log WHERE action = 'SANCTION_LIFT'")).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-21] 감사 로그를 최근 기록부터 돌려주고 adminId, targetType, 기간 필터와 페이지를 적용한다")
    void auditLogsAreFilteredAndPaged() {
        long reportId = insertReport("NO_SHOW", "RECEIVED", BASE);
        post(REPORTS + "/" + reportId + "/start-review", null);
        post(
                REPORTS + "/" + reportId + "/action",
                "{\"sanction\":{\"type\":\"WARNING\",\"reason\":\"" + SECRET_REASON + "\"}}");
        long sanctionId = sanctionOfReport(reportId);
        post(SANCTIONS + "/" + sanctionId + "/lift", null);

        MvcTestResult all = get(AUDIT_LOGS);

        assertThat(all).hasStatus(HttpStatus.OK);
        assertThat((List<String>) json(all, "$.content[*].action"))
                .containsExactly("SANCTION_LIFT", "REPORT_ACTION", "REPORT_START_REVIEW");
        assertThat(all).bodyJson().extractingPath("$.content[0].adminId").isEqualTo((int) adminId);
        assertThat(all).bodyJson().extractingPath("$.content[0].targetType").isEqualTo("SANCTION");
        assertThat(all).bodyJson().extractingPath("$.content[0].targetId").isEqualTo((int) sanctionId);
        assertThat(all)
                .bodyJson()
                .extractingPath("$.content[1].detail.sanctionType")
                .isEqualTo("WARNING");
        assertThat(all)
                .bodyJson()
                .extractingPath("$.content[2].detail.toStatus")
                .isEqualTo("IN_REVIEW");
        assertThat(body(all)).doesNotContain(SECRET_REASON);
        assertThat((List<String>) json(get(AUDIT_LOGS + "?targetType=MEMBER_REPORT"), "$.content[*].action"))
                .containsExactly("REPORT_ACTION", "REPORT_START_REVIEW");
        assertThat((List<?>) json(get(AUDIT_LOGS + "?adminId=" + reporterId), "$.content"))
                .isEmpty();
        assertThat((List<?>) json(get(AUDIT_LOGS + "?adminId=" + adminId), "$.content"))
                .hasSize(3);
        MvcTestResult firstPage = get(AUDIT_LOGS + "?size=2");
        assertThat((List<?>) json(firstPage, "$.content")).hasSize(2);
        assertThat(firstPage).bodyJson().extractingPath("$.hasNext").isEqualTo(true);
        assertThat((List<?>) json(get(AUDIT_LOGS + "?size=2&page=1"), "$.content"))
                .hasSize(1);
    }

    @Test
    @DisplayName("[F-21] 감사 로그의 기간 필터는 from 이상 to 미만이다")
    void auditLogPeriodBoundaries() {
        long reportId = insertReport("NO_SHOW", "RECEIVED", BASE);
        post(REPORTS + "/" + reportId + "/start-review", null);
        Instant recorded = clock.instant();

        assertThat((List<?>) json(get(AUDIT_LOGS + "?from=" + recorded), "$.content"))
                .hasSize(1);
        assertThat((List<?>) json(get(AUDIT_LOGS + "?from=" + recorded.plusSeconds(1)), "$.content"))
                .isEmpty();
        assertThat((List<?>) json(get(AUDIT_LOGS + "?to=" + recorded), "$.content"))
                .isEmpty();
        assertThat((List<?>) json(get(AUDIT_LOGS + "?to=" + recorded.plusSeconds(1)), "$.content"))
                .hasSize(1);
        MvcTestResult invalid = get(AUDIT_LOGS + "?from=yesterday");
        assertThat(invalid).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(invalid).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
    }

    // 신고 한 건을 JDBC로 직접 넣는다. 신고자는 신고마다 새 회원이라서 같은 대상을 여러 번 신고해도 중복 제약에 걸리지 않는다.
    private long insertReport(String type, String status, Instant createdAt) {
        long newReporter = fixture.saveVerifiedMember(TestSequence.nickname());
        return reportFixture.insertMemberReport(newReporter, targetId, basecampId, type, status, createdAt);
    }

    // 실제 신고 접수 서비스로 성희롱·위협 신고를 접수한다. 대상은 72시간 임시 정지된다.
    private long urgentReport(long target) {
        long newReporter = fixture.saveVerifiedMember(TestSequence.nickname());
        long newBasecamp = fixture.saveBasecamp("COMPLETED", BASE);
        fixture.insertMember(newBasecamp, newReporter, "MEMBER", "ACTIVE");
        fixture.insertMember(newBasecamp, target, "MEMBER", "ACTIVE");
        return memberReportService.report(
                newReporter,
                new MemberReportCommand(
                        target, newBasecamp, ReportKind.MEMBER, null, ReportType.HARASSMENT_OR_THREAT, "위협했다"));
    }

    private long confirm(SanctionType type) {
        return sanctionConfirmService
                .confirm(new SanctionConfirmCommand(targetId, null, type, "사유", adminId))
                .sanctionId();
    }

    private long ledBasecamp(long leaderId) {
        long ledBasecampId = fixture.saveBasecamp("RECRUITING", null);
        jdbc.update(
                "UPDATE basecamp_member SET member_id = ? WHERE basecamp_id = ? AND role = 'LEADER'",
                leaderId,
                ledBasecampId);
        jdbc.update("UPDATE basecamp SET leader_id = ? WHERE id = ?", leaderId, ledBasecampId);
        return ledBasecampId;
    }

    private MvcTestResult get(String uri) {
        return mvc.get().uri(uri).cookie(adminSession).exchange();
    }

    private MvcTestResult post(String uri, String body) {
        var request = mvc.post().uri(uri).cookie(adminSession).with(csrf());
        if (body == null) {
            return request.exchange();
        }
        return request.contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private static String body(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private static <T> T json(MvcTestResult result, String path) {
        return JsonPath.read(body(result), path);
    }

    private static List<Long> ids(MvcTestResult result, String path) {
        List<Number> numbers = json(result, path);
        return numbers.stream().map(Number::longValue).toList();
    }

    private Map<String, Object> resolvedPayload() {
        String payload = jdbc.queryForObject(
                "SELECT payload FROM outbox_event WHERE event_type = 'MEMBER_REPORT_RESOLVED'", String.class);
        return JsonPath.read(payload, "$");
    }

    private void assertAuditRow(String action, long reportId) {
        assertThat(jdbc.queryForObject(
                        "SELECT CONCAT(admin_id, ':', target_type, ':', target_id) FROM admin_audit_log"
                                + " WHERE action = ?",
                        String.class,
                        action))
                .isEqualTo(adminId + ":MEMBER_REPORT:" + reportId);
    }

    private String allAuditDetails() {
        return String.join("\n", jdbc.queryForList("SELECT IFNULL(detail, '') FROM admin_audit_log", String.class));
    }

    private int count(String tableAndCondition) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tableAndCondition, Integer.class);
    }

    private String reportStatus(long reportId) {
        return jdbc.queryForObject("SELECT status FROM member_report WHERE id = ?", String.class, reportId);
    }

    private String memberStatus(long memberId) {
        return jdbc.queryForObject("SELECT status FROM member WHERE id = ?", String.class, memberId);
    }

    private Instant suspendedUntil(long memberId) {
        return jdbc.queryForObject(
                                "SELECT suspended_until FROM member WHERE id = ?", java.sql.Timestamp.class, memberId)
                        == null
                ? null
                : jdbc.queryForObject(
                                "SELECT suspended_until FROM member WHERE id = ?",
                                java.time.LocalDateTime.class,
                                memberId)
                        .toInstant(java.time.ZoneOffset.UTC);
    }

    private List<String> sanctionStatuses(long memberId) {
        return jdbc.queryForList("SELECT status FROM sanction WHERE member_id = ? ORDER BY id", String.class, memberId);
    }

    private long sanctionOfReport(long reportId) {
        return jdbc.queryForObject("SELECT id FROM sanction WHERE report_id = ?", Long.class, reportId);
    }

    private String sanctionStatus(long sanctionId) {
        return jdbc.queryForObject("SELECT status FROM sanction WHERE id = ?", String.class, sanctionId);
    }

    private Instant sanctionEnd(long reportId) {
        return jdbc.queryForObject(
                        "SELECT ends_at FROM sanction WHERE report_id = ?", java.time.LocalDateTime.class, reportId)
                .toInstant(java.time.ZoneOffset.UTC);
    }

    private String basecampStatus(long basecampId) {
        return jdbc.queryForObject("SELECT status FROM basecamp WHERE id = ?", String.class, basecampId);
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
