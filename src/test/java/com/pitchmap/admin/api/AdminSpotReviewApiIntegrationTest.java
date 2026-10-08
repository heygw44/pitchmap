package com.pitchmap.admin.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.spot.application.BakjiFeedbackService;
import com.pitchmap.spot.application.BakjiProblemReportCommand;
import com.pitchmap.spot.domain.SpotErrorCode;
import com.pitchmap.trust.application.CompanionReviewFixture;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
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
class AdminSpotReviewApiIntegrationTest {

    private static final String SPOTS = "/api/admin/spots";
    private static final String AUDIT_LOGS = "/api/admin/audit-logs";
    private static final String PASSWORD = "Valid-pass1";
    private static final String SESSION_COOKIE = "SESSION";
    private static final String SECRET_CONTENT = "비밀신고내용이들어간문장";
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
    private BakjiFeedbackService bakjiFeedbackService;

    private CompanionReviewFixture fixture;
    private long adminId;
    private Cookie adminSession;
    private long bakjiReporterId;

    @BeforeEach
    void setUp() {
        fixture = new CompanionReviewFixture(jdbc, memberRepository);
        adminId = fixture.saveVerifiedMember(TestSequence.nickname());
        jdbc.update("UPDATE member SET role = 'ADMIN' WHERE id = ?", adminId);
        adminSession = login(adminId);
        bakjiReporterId = fixture.saveVerifiedMember(TestSequence.nickname());
    }

    @Test
    @DisplayName("[F-21] 검토 대기 박지는 제보자, 검토 전 신고 수, 사유별 수, 최근 신고 5건(최신순)과 함께 목록에 나오고 ACTIVE 장소는 나오지 않는다")
    void listShowsPendingReviewSpotWithReports() {
        // given
        long pending = insertBakji("PENDING_REVIEW", BASE);
        insertReport(pending, "ILLEGAL_AREA", SECRET_CONTENT + "1", BASE.plus(Duration.ofHours(1)), null);
        insertReport(pending, "CLOSED", SECRET_CONTENT + "2", BASE.plus(Duration.ofHours(2)), null);
        insertReport(pending, "FALSE_INFO", SECRET_CONTENT + "3", BASE.plus(Duration.ofHours(3)), null);
        insertReport(pending, "FALSE_INFO", SECRET_CONTENT + "4", BASE.plus(Duration.ofHours(4)), null);
        insertReport(pending, "ILLEGAL_AREA", SECRET_CONTENT + "5", BASE.plus(Duration.ofHours(5)), null);
        insertReport(pending, "ILLEGAL_AREA", SECRET_CONTENT + "6", BASE.plus(Duration.ofHours(6)), null);
        insertReport(pending, "CLOSED", "검토를 마친 신고", BASE.plus(Duration.ofHours(7)), BASE.plus(Duration.ofDays(1)));
        insertBakji("ACTIVE", BASE);

        // when
        MvcTestResult result = get(SPOTS);

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(ids(result, "$.content[*].spotId")).containsExactly(pending);
        assertThat(result).bodyJson().extractingPath("$.content[0].type").isEqualTo("BAKJI");
        assertThat(result).bodyJson().extractingPath("$.content[0].status").isEqualTo("PENDING_REVIEW");
        assertThat(result).bodyJson().extractingPath("$.content[0].lat").isEqualTo(37.25);
        assertThat(result).bodyJson().extractingPath("$.content[0].lng").isEqualTo(127.25);
        assertThat(result).bodyJson().extractingPath("$.content[0].parkWarning").isEqualTo(false);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].reporter.memberId")
                .isEqualTo((int) bakjiReporterId);
        assertThat(result).bodyJson().extractingPath("$.content[0].reportCount").isEqualTo(6);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].reasonCounts.ILLEGAL_AREA")
                .isEqualTo(3);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].reasonCounts.CLOSED")
                .isEqualTo(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].reasonCounts.FALSE_INFO")
                .isEqualTo(2);
        List<String> recentContents = json(result, "$.content[0].recentReports[*].content");
        assertThat(recentContents)
                .containsExactly(
                        SECRET_CONTENT + "6",
                        SECRET_CONTENT + "5",
                        SECRET_CONTENT + "4",
                        SECRET_CONTENT + "3",
                        SECRET_CONTENT + "2");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].recentReports[0].reason")
                .isEqualTo("ILLEGAL_AREA");
        assertThat(result).bodyJson().doesNotHavePath("$.content[0].recentReports[0].reporterId");
        assertThat(result).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("[F-21] 목록은 상태가 바뀐 시각이 이른 순서이고, 공공데이터 장소는 reporter가 null이며, 페이지를 넘기면 hasNext를 알린다")
    void listIsOrderedByStatusChangeAndPaged() {
        // given
        long newer = insertBakji("PENDING_REVIEW", BASE.plus(Duration.ofHours(2)));
        long older = insertBakji("PENDING_REVIEW", BASE);
        long publicSpot = insertCampsite("PENDING_REVIEW", BASE.plus(Duration.ofHours(1)));

        // when
        MvcTestResult all = get(SPOTS);
        MvcTestResult first = get(SPOTS + "?size=2");
        MvcTestResult second = get(SPOTS + "?size=2&page=1");

        // then
        assertThat(ids(all, "$.content[*].spotId")).containsExactly(older, publicSpot, newer);
        assertThat(all).bodyJson().extractingPath("$.content[1].reporter").isNull();
        assertThat(all).bodyJson().extractingPath("$.content[1].reportCount").isEqualTo(0);
        assertThat(all)
                .bodyJson()
                .extractingPath("$.content[1].recentReports")
                .asList()
                .isEmpty();
        assertThat(ids(first, "$.content[*].spotId")).containsExactly(older, publicSpot);
        assertThat(first).bodyJson().extractingPath("$.hasNext").isEqualTo(true);
        assertThat(ids(second, "$.content[*].spotId")).containsExactly(newer);
        assertThat(second).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("[F-21] status=HIDDEN이면 숨긴 장소만 나오고, 허용하지 않는 status와 범위를 벗어난 size는 400 INVALID_INPUT이다")
    void listFiltersByStatusAndValidatesInput() {
        long hidden = insertBakji("HIDDEN", BASE);
        insertBakji("PENDING_REVIEW", BASE);

        assertThat(ids(get(SPOTS + "?status=HIDDEN"), "$.content[*].spotId")).containsExactly(hidden);
        for (String query : List.of("?status=ACTIVE", "?status=DELETED", "?status=hidden", "?size=51", "?size=0")) {
            MvcTestResult result = get(SPOTS + query);
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
    }

    @Test
    @DisplayName("[F-21] 검토 대기 박지를 숨기면 HIDDEN이 되어 상세가 404이고 status=HIDDEN 목록에 나오며 감사 로그 한 줄이 남는다")
    void hideMakesSpotInvisibleAndWritesAuditLog() {
        // given
        long spotId = insertBakji("PENDING_REVIEW", BASE);
        insertReport(spotId, "CLOSED", SECRET_CONTENT, BASE, null);

        // when
        MvcTestResult result = post(SPOTS + "/" + spotId + "/hide");

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.spotId").isEqualTo((int) spotId);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("HIDDEN");
        assertThat(statusOf(spotId)).isEqualTo("HIDDEN");
        assertThat(mvc.get().uri("/api/spots/" + spotId).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(ids(get(SPOTS + "?status=HIDDEN"), "$.content[*].spotId")).containsExactly(spotId);
        assertThat(ids(get(SPOTS), "$.content[*].spotId")).isEmpty();
        assertThat(count("admin_audit_log WHERE action = 'SPOT_HIDE'")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT CONCAT(admin_id, ':', target_type, ':', target_id) FROM admin_audit_log"
                                + " WHERE action = 'SPOT_HIDE'",
                        String.class))
                .isEqualTo(adminId + ":SPOT:" + spotId);
        String detail =
                jdbc.queryForObject("SELECT detail FROM admin_audit_log WHERE action = 'SPOT_HIDE'", String.class);
        assertThat((String) JsonPath.read(detail, "$.spotType")).isEqualTo("BAKJI");
        assertThat((String) JsonPath.read(detail, "$.fromStatus")).isEqualTo("PENDING_REVIEW");
        assertThat((String) JsonPath.read(detail, "$.toStatus")).isEqualTo("HIDDEN");
        assertThat(detail).doesNotContain(SECRET_CONTENT);
        MvcTestResult logs = get(AUDIT_LOGS + "?targetType=SPOT");
        assertThat(logs).hasStatus(HttpStatus.OK);
        assertThat(logs).bodyJson().extractingPath("$.content[0].action").isEqualTo("SPOT_HIDE");
    }

    @Test
    @DisplayName("[F-21] ACTIVE 박지와 공공데이터 장소도 숨길 수 있다")
    void hideWorksForActiveBakjiAndPublicSpot() {
        long active = insertBakji("ACTIVE", BASE);
        long campsite = insertCampsite("ACTIVE", BASE);

        assertThat(post(SPOTS + "/" + active + "/hide")).hasStatus(HttpStatus.OK);
        assertThat(post(SPOTS + "/" + campsite + "/hide")).hasStatus(HttpStatus.OK);

        assertThat(statusOf(active)).isEqualTo("HIDDEN");
        assertThat(statusOf(campsite)).isEqualTo("HIDDEN");
        assertThat(count("admin_audit_log WHERE action = 'SPOT_HIDE'")).isEqualTo(2);
    }

    @Test
    @DisplayName("[F-21] 검토 대기 박지를 복구하면 ACTIVE가 되어 상세가 200이고, 검토 전 신고가 검토 완료로 표시되며 감사 로그에 건수가 남는다")
    void restoreMakesSpotVisibleAndMarksReportsReviewed() {
        // given
        long spotId = insertBakji("PENDING_REVIEW", BASE);
        for (int i = 0; i < 5; i++) {
            insertReport(spotId, "FALSE_INFO", SECRET_CONTENT, BASE.plus(Duration.ofMinutes(i)), null);
        }
        insertReport(spotId, "CLOSED", "이전에 검토한 신고", BASE, BASE.plus(Duration.ofHours(1)));

        // when
        MvcTestResult result = post(SPOTS + "/" + spotId + "/restore");

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
        assertThat(statusOf(spotId)).isEqualTo("ACTIVE");
        assertThat(mvc.get().uri("/api/spots/" + spotId).exchange()).hasStatus(HttpStatus.OK);
        assertThat(count("bakji_report WHERE spot_id = " + spotId)).isEqualTo(6);
        assertThat(count("bakji_report WHERE spot_id = " + spotId + " AND reviewed_at IS NULL"))
                .isZero();
        String detail =
                jdbc.queryForObject("SELECT detail FROM admin_audit_log WHERE action = 'SPOT_RESTORE'", String.class);
        assertThat((String) JsonPath.read(detail, "$.fromStatus")).isEqualTo("PENDING_REVIEW");
        assertThat((String) JsonPath.read(detail, "$.toStatus")).isEqualTo("ACTIVE");
        assertThat((Integer) JsonPath.read(detail, "$.reviewedReportCount")).isEqualTo(5);
        assertThat(detail).doesNotContain(SECRET_CONTENT);
        assertThat(ids(get(SPOTS), "$.content[*].spotId")).isEmpty();
    }

    @Test
    @DisplayName("[F-08][F-21] 복구한 박지는 새 신고 1건으로는 검토 대기가 되지 않고 새 신고 5건이면 되며, 이미 신고한 회원은 다시 신고할 수 없다")
    void afterRestoreOnlyNewReportsCountTowardPendingReview() {
        // given
        long spotId = insertBakji("PENDING_REVIEW", BASE);
        long firstReporter = insertReport(spotId, "CLOSED", null, BASE, null);
        for (int i = 1; i < 5; i++) {
            insertReport(spotId, "CLOSED", null, BASE.plus(Duration.ofMinutes(i)), null);
        }
        assertThat(post(SPOTS + "/" + spotId + "/restore")).hasStatus(HttpStatus.OK);
        BakjiProblemReportCommand report = new BakjiProblemReportCommand("CLOSED", "다시 확인했다");

        // when
        bakjiFeedbackService.reportProblem(newMemberId(), spotId, report);

        // then
        assertThat(statusOf(spotId)).isEqualTo("ACTIVE");
        assertThatThrownBy(() -> bakjiFeedbackService.reportProblem(firstReporter, spotId, report))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(SpotErrorCode.BAKJI_ALREADY_REPORTED));

        // when
        for (int i = 0; i < 4; i++) {
            bakjiFeedbackService.reportProblem(newMemberId(), spotId, report);
        }

        // then
        assertThat(statusOf(spotId)).isEqualTo("PENDING_REVIEW");
        assertThat(count("bakji_report WHERE spot_id = " + spotId + " AND reviewed_at IS NULL"))
                .isEqualTo(5);
        MvcTestResult list = get(SPOTS);
        assertThat(list).bodyJson().extractingPath("$.content[0].reportCount").isEqualTo(5);
    }

    @Test
    @DisplayName("[F-06][F-21] 원천 삭제로 숨겨진 공공데이터 장소를 복구하면 ACTIVE가 되고 source_removed_at이 비워진다")
    void restoreClearsSourceRemovedOfPublicSpot() {
        // given
        long spotId = insertCampsite("HIDDEN", BASE);
        jdbc.update("UPDATE public_spot_detail SET source_removed_at = ? WHERE spot_id = ?", BASE, spotId);

        // when
        MvcTestResult result = post(SPOTS + "/" + spotId + "/restore");

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(statusOf(spotId)).isEqualTo("ACTIVE");
        assertThat(sourceRemovedAt(spotId)).isNull();
        String detail =
                jdbc.queryForObject("SELECT detail FROM admin_audit_log WHERE action = 'SPOT_RESTORE'", String.class);
        assertThat((String) JsonPath.read(detail, "$.spotType")).isEqualTo("CAMPSITE");
        assertThat((Integer) JsonPath.read(detail, "$.reviewedReportCount")).isZero();
    }

    @Test
    @DisplayName("[F-06][F-21] 원천 삭제 표시가 있는 장소를 관리자가 숨기면 표시가 비워진다")
    void hideClearsSourceRemovedOfPublicSpot() {
        long spotId = insertCampsite("ACTIVE", BASE);
        jdbc.update("UPDATE public_spot_detail SET source_removed_at = ? WHERE spot_id = ?", BASE, spotId);

        assertThat(post(SPOTS + "/" + spotId + "/hide")).hasStatus(HttpStatus.OK);

        assertThat(sourceRemovedAt(spotId)).isNull();
    }

    @Test
    @DisplayName("[F-21] 없는 장소나 삭제한 박지를 숨기거나 복구하면 404 NOT_FOUND이고 감사 로그를 남기지 않는다")
    void missingAndDeletedSpotsAreNotFound() {
        long deleted = insertBakji("DELETED", BASE);

        for (long spotId : new long[] {9_999_999L, deleted}) {
            for (String action : List.of("hide", "restore")) {
                MvcTestResult result = post(SPOTS + "/" + spotId + "/" + action);

                assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
                assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
            }
        }
        assertThat(statusOf(deleted)).isEqualTo("DELETED");
        assertThat(count("admin_audit_log")).isZero();
    }

    @Test
    @DisplayName("[F-21] 숨긴 장소를 또 숨기거나 ACTIVE 장소를 복구하면 409 SPOT_INVALID_STATE이고 상태와 감사 로그는 그대로다")
    void invalidTransitionsAreConflicts() {
        long hidden = insertBakji("HIDDEN", BASE);
        long active = insertBakji("ACTIVE", BASE);
        insertReport(active, "CLOSED", null, BASE, null);

        MvcTestResult hideHidden = post(SPOTS + "/" + hidden + "/hide");
        MvcTestResult restoreActive = post(SPOTS + "/" + active + "/restore");

        assertThat(hideHidden).hasStatus(HttpStatus.CONFLICT);
        assertThat(hideHidden).bodyJson().extractingPath("$.code").isEqualTo("SPOT_INVALID_STATE");
        assertThat(restoreActive).hasStatus(HttpStatus.CONFLICT);
        assertThat(restoreActive).bodyJson().extractingPath("$.code").isEqualTo("SPOT_INVALID_STATE");
        assertThat(statusOf(hidden)).isEqualTo("HIDDEN");
        assertThat(statusOf(active)).isEqualTo("ACTIVE");
        assertThat(count("bakji_report WHERE reviewed_at IS NOT NULL")).isZero();
        assertThat(count("admin_audit_log")).isZero();
    }

    @Test
    @DisplayName("[F-21] 관리자가 아닌 회원은 박지 검토 API를 쓸 수 없다")
    void memberCannotUseSpotReviewApi() {
        long spotId = insertBakji("PENDING_REVIEW", BASE);
        Cookie member = login(fixture.saveVerifiedMember(TestSequence.nickname()));

        MvcTestResult list = mvc.get().uri(SPOTS).cookie(member).exchange();
        MvcTestResult hide = mvc.post()
                .uri(SPOTS + "/" + spotId + "/hide")
                .cookie(member)
                .with(csrf())
                .exchange();

        assertThat(list).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(hide).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(statusOf(spotId)).isEqualTo("PENDING_REVIEW");
    }

    private long insertBakji(String status, Instant updatedAt) {
        String name = TestSequence.unique("박지");
        jdbc.update(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                        + " VALUES ('BAKJI', ?, ST_SRID(POINT(127.25, 37.25), 4326), 60, 127, ?, ?, ?)",
                name,
                status,
                BASE,
                updatedAt);
        long spotId = jdbc.queryForObject("SELECT id FROM spot WHERE name = ?", Long.class, name);
        jdbc.update(
                "INSERT INTO bakji_detail (spot_id, reporter_id, has_water, has_toilet, created_at, updated_at)"
                        + " VALUES (?, ?, TRUE, FALSE, ?, ?)",
                spotId,
                bakjiReporterId,
                BASE,
                BASE);
        return spotId;
    }

    private long insertCampsite(String status, Instant updatedAt) {
        String name = TestSequence.unique("야영장");
        jdbc.update(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                        + " VALUES ('CAMPSITE', ?, ST_SRID(POINT(127.3, 37.3), 4326), 60, 127, ?, ?, ?)",
                name,
                status,
                BASE,
                updatedAt);
        long spotId = jdbc.queryForObject("SELECT id FROM spot WHERE name = ?", Long.class, name);
        jdbc.update(
                "INSERT INTO public_spot_detail (spot_id, source, external_id, synced_at, created_at, updated_at)"
                        + " VALUES (?, 'GOCAMPING', ?, ?, ?, ?)",
                spotId,
                TestSequence.unique("ext"),
                BASE,
                BASE,
                BASE);
        return spotId;
    }

    // 신고마다 새 회원이 신고한다. 한 회원이 같은 박지를 두 번 신고할 수 없기 때문이다. 신고한 회원의 ID를 돌려준다.
    private long insertReport(long spotId, String reason, String content, Instant createdAt, Instant reviewedAt) {
        long reporterId = newMemberId();
        jdbc.update(
                "INSERT INTO bakji_report (spot_id, reporter_id, reason, content, created_at, reviewed_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                spotId,
                reporterId,
                reason,
                content,
                createdAt,
                reviewedAt);
        return reporterId;
    }

    private long newMemberId() {
        return fixture.saveVerifiedMember(TestSequence.nickname());
    }

    private MvcTestResult get(String uri) {
        return mvc.get().uri(uri).cookie(adminSession).exchange();
    }

    private MvcTestResult post(String uri) {
        return mvc.post().uri(uri).cookie(adminSession).with(csrf()).exchange();
    }

    private static <T> T json(MvcTestResult result, String path) {
        return JsonPath.read(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), path);
    }

    private static List<Long> ids(MvcTestResult result, String path) {
        List<Number> numbers = json(result, path);
        return numbers.stream().map(Number::longValue).toList();
    }

    private String statusOf(long spotId) {
        return jdbc.queryForObject("SELECT status FROM spot WHERE id = ?", String.class, spotId);
    }

    private Object sourceRemovedAt(long spotId) {
        return jdbc.queryForObject(
                "SELECT source_removed_at FROM public_spot_detail WHERE spot_id = ?", Object.class, spotId);
    }

    private int count(String tableAndCondition) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tableAndCondition, Integer.class);
    }

    private Cookie login(long memberId) {
        jdbc.update("UPDATE member SET password_hash = ? WHERE id = ?", passwordEncoder.encode(PASSWORD), memberId);
        String email = jdbc.queryForObject("SELECT email FROM member WHERE id = ?", String.class, memberId);
        String body = "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD);
        MvcTestResult result = mvc.post()
                .uri("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        Cookie session = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        return session;
    }
}
