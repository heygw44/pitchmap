package com.pitchmap.admin.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.trust.application.CompanionReviewFixture;
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
class AdminProgramApiIntegrationTest {

    private static final String ADMIN_PROGRAMS = "/api/admin/programs";
    private static final String PROGRAMS = "/api/programs";
    private static final String PASSWORD = "Valid-pass1";
    private static final String SESSION_COOKIE = "SESSION";

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

    private CompanionReviewFixture fixture;
    private long adminId;
    private Cookie adminSession;

    @BeforeEach
    void setUp() {
        fixture = new CompanionReviewFixture(jdbc, memberRepository);
        adminId = fixture.saveVerifiedMember(TestSequence.nickname());
        jdbc.update("UPDATE member SET role = 'ADMIN' WHERE id = ?", adminId);
        adminSession = login(adminId);
    }

    @Test
    @DisplayName("[F-17] 관리자가 행사를 등록하면 저장되고 신청 시작 전이라 UPCOMING이며, 결제 기한은 15분으로 정해진다")
    void adminCreatesProgram() {
        // given
        Instant now = clock.instant();

        // when
        MvcTestResult result = createProgram(body(now.plus(Duration.ofDays(1)), 200, null));

        // then
        assertThat(result).hasStatus(HttpStatus.CREATED);
        long programId = programId(result);
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM program WHERE id = ?", programId);
        assertThat(row.get("title")).isEqualTo("가을 백패킹");
        assertThat(row.get("capacity")).isEqualTo(200);
        assertThat(row.get("fee")).isEqualTo(30000);
        assertThat(row.get("payment_deadline_minutes")).isEqualTo(15);
        assertThat(row.get("status")).isEqualTo("SCHEDULED");
        assertThat(row.get("created_by")).isEqualTo(adminId);
        MvcTestResult detail = mvc.get().uri(PROGRAMS + "/" + programId).exchange();
        assertThat(detail).hasStatus(HttpStatus.OK);
        assertThat(detail).bodyJson().extractingPath("$.status").isEqualTo("UPCOMING");
        assertThat(detail).bodyJson().extractingPath("$.remainingSeats").isEqualTo(200);
        assertThat(detail).bodyJson().extractingPath("$.paymentDeadlineMinutes").isEqualTo(15);
        assertThat(detail).bodyJson().extractingPath("$.spotId").isNull();
        assertThat(detail).bodyJson().doesNotHavePath("$.myApplication");
    }

    @Test
    @DisplayName("[F-17] 시각 순서가 틀리거나 행사 시작이 과거이거나 범위를 벗어난 값이면 400 INVALID_INPUT이고 저장하지 않는다")
    void createRejectsInvalidValues() {
        // given
        Instant now = clock.instant();
        String closeAfterStart = bodyFull(
                now.plus(Duration.ofDays(1)),
                now.plus(Duration.ofDays(20)),
                now.plus(Duration.ofDays(14)),
                now.plus(Duration.ofDays(15)),
                200,
                null);
        String pastStart = bodyFull(
                now.minus(Duration.ofDays(5)),
                now.minus(Duration.ofDays(2)),
                now.minus(Duration.ofDays(1)),
                now.plus(Duration.ofDays(1)),
                200,
                null);

        // when
        MvcTestResult order = createProgram(closeAfterStart);
        MvcTestResult past = createProgram(pastStart);
        MvcTestResult range = createProgram(body(now.plus(Duration.ofDays(1)), 1001, null));

        // then
        for (MvcTestResult result : List.of(order, past, range)) {
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
        assertThat(count("program")).isZero();
        assertThat(count("admin_audit_log")).isZero();
    }

    @Test
    @DisplayName("[F-17] 지도에 보이는 장소만 연결할 수 있고, 숨김이거나 없는 장소는 400 INVALID_INPUT이다")
    void createLinksOnlyActiveSpot() {
        // given
        Instant open = clock.instant().plus(Duration.ofDays(1));
        long active = insertSpot("ACTIVE");
        long hidden = insertSpot("HIDDEN");

        // when
        MvcTestResult linked = createProgram(body(open, 200, active));
        MvcTestResult hiddenResult = createProgram(body(open, 200, hidden));
        MvcTestResult missing = createProgram(body(open, 200, 999_999L));

        // then
        assertThat(linked).hasStatus(HttpStatus.CREATED);
        MvcTestResult detail = mvc.get().uri(PROGRAMS + "/" + programId(linked)).exchange();
        assertThat(detail).bodyJson().extractingPath("$.spotId").isEqualTo((int) active);
        for (MvcTestResult result : List.of(hiddenResult, missing)) {
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
        assertThat(count("program")).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-17][PG-01] 신청이 시작된 행사의 정원을 줄이면 409 PROGRAM_CAPACITY_DECREASE이고, 늘리면 200이다")
    void capacityCannotDecreaseAfterApplyOpens() {
        // given
        long programId = programId(createProgram(body(clock.instant().minus(Duration.ofHours(1)), 100, null)));

        // when
        MvcTestResult decrease = patch(programId, "{\"capacity\":99}");
        MvcTestResult increase = patch(programId, "{\"capacity\":150}");

        // then
        assertThat(decrease).hasStatus(HttpStatus.CONFLICT);
        assertThat(decrease).bodyJson().extractingPath("$.code").isEqualTo("PROGRAM_CAPACITY_DECREASE");
        assertThat(increase).hasStatus(HttpStatus.OK);
        assertThat(increase).bodyJson().extractingPath("$.capacity").isEqualTo(150);
        assertThat(increase).bodyJson().extractingPath("$.remainingSeats").isEqualTo(150);
        assertThat(increase).bodyJson().extractingPath("$.status").isEqualTo("OPEN");
        assertThat(increase).bodyJson().doesNotHavePath("$.myApplication");
        assertThat(capacityOf(programId)).isEqualTo(150);
    }

    @Test
    @DisplayName("[F-17] 신청 시작 전에는 정원을 줄일 수 있다")
    void capacityCanDecreaseBeforeApplyOpens() {
        // given
        long programId = programId(createProgram(body(clock.instant().plus(Duration.ofDays(1)), 100, null)));

        // when
        MvcTestResult result = patch(programId, "{\"capacity\":10}");

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(capacityOf(programId)).isEqualTo(10);
    }

    @Test
    @DisplayName("[F-17] 수정은 보낸 필드만 바꾸고, 고친 뒤의 시각 순서가 틀리면 400이며, spotId를 null로 보내면 연결을 끊는다")
    void reviseMergesFieldsAndValidates() {
        // given
        Instant now = clock.instant();
        long spotId = insertSpot("ACTIVE");
        long programId = programId(createProgram(body(now.plus(Duration.ofDays(1)), 100, spotId)));

        // when
        MvcTestResult title = patch(programId, "{\"title\":\"새 제목\",\"fee\":0}");
        MvcTestResult badOrder = patch(programId, "{\"endAt\":\"" + now.plus(Duration.ofDays(13)) + "\"}");
        MvcTestResult hiddenSpot = patch(programId, "{\"spotId\":" + insertSpot("HIDDEN") + "}");
        MvcTestResult cleared = patch(programId, "{\"spotId\":null}");
        MvcTestResult empty = patch(programId, "{}");

        // then
        assertThat(title).hasStatus(HttpStatus.OK);
        assertThat(title).bodyJson().extractingPath("$.title").isEqualTo("새 제목");
        assertThat(title).bodyJson().extractingPath("$.fee").isEqualTo(0);
        assertThat(title).bodyJson().extractingPath("$.spotId").isEqualTo((int) spotId);
        assertThat(title).bodyJson().extractingPath("$.description").isEqualTo("설명");
        for (MvcTestResult result : List.of(badOrder, hiddenSpot, empty)) {
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
        assertThat(cleared).hasStatus(HttpStatus.OK);
        assertThat(cleared).bodyJson().extractingPath("$.spotId").isNull();
        assertThat(jdbc.queryForObject("SELECT spot_id FROM program WHERE id = ?", Long.class, programId))
                .isNull();
        assertThat(patch(999_999L, "{\"fee\":0}")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("[F-17] 신청자 목록은 신청 순서로 나오고 status로 거르며 닉네임만 담고 이메일은 담지 않는다")
    void applicationsAreFilteredByStatus() {
        // given
        Instant now = clock.instant();
        long programId = programId(createProgram(body(now.minus(Duration.ofHours(1)), 10, null)));
        long first = insertApplication(programId, "PENDING_PAYMENT", now);
        long second = insertApplication(programId, "CONFIRMED", now);
        long third = insertApplication(programId, "EXPIRED", now);
        long fourth = insertApplication(programId, "CANCELED", now);
        long otherProgram = programId(createProgram(body(now.plus(Duration.ofDays(1)), 10, null)));
        insertApplication(otherProgram, "CONFIRMED", now);

        // when
        MvcTestResult all = get(ADMIN_PROGRAMS + "/" + programId + "/applications");
        MvcTestResult confirmed = get(ADMIN_PROGRAMS + "/" + programId + "/applications?status=CONFIRMED");
        MvcTestResult paged = get(ADMIN_PROGRAMS + "/" + programId + "/applications?size=3&page=1");
        MvcTestResult unknown = get(ADMIN_PROGRAMS + "/" + programId + "/applications?status=PENDING");

        // then
        assertThat(ids(all, "$.content[*].applicationId")).containsExactly(first, second, third, fourth);
        assertThat(ids(confirmed, "$.content[*].applicationId")).containsExactly(second);
        assertThat(ids(paged, "$.content[*].applicationId")).containsExactly(fourth);
        assertThat(paged).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        assertThat(all).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        String nickname =
                jdbc.queryForObject("SELECT nickname FROM member WHERE id = ?", String.class, memberIdOf(first));
        assertThat(all)
                .bodyJson()
                .extractingPath("$.content[0].member.nickname")
                .isEqualTo(nickname);
        assertThat(all).bodyJson().doesNotHavePath("$.content[0].member.email");
        assertThat(all).bodyJson().extractingPath("$.content[0].status").isEqualTo("PENDING_PAYMENT");
        assertThat(all).bodyJson().extractingPath("$.content[0].cancelReason").isNull();
        assertThat(unknown).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(get(ADMIN_PROGRAMS + "/999999/applications")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("[F-17] 행사를 취소하면 결제 대기·확정 신청은 PROGRAM_CANCELED로 취소되고 결제는 환불되며, 만료·취소된 신청은 그대로이고 다시 취소하면 409이다")
    void cancelCancelsActiveApplicationsAndRefundsPayments() {
        // given
        Instant now = clock.instant();
        long programId = programId(createProgram(body(now.minus(Duration.ofHours(1)), 10, null)));
        long pending = insertApplication(programId, "PENDING_PAYMENT", now);
        long confirmed = insertApplication(programId, "CONFIRMED", now);
        long expired = insertApplication(programId, "EXPIRED", now);
        long canceledByUser = insertApplication(programId, "CANCELED", now);
        jdbc.update(
                "UPDATE program_application SET cancel_reason = 'USER', canceled_at = ? WHERE id = ?",
                now.minus(Duration.ofHours(2)),
                canceledByUser);
        insertPayment(confirmed, "PAID");
        insertPayment(canceledByUser, "REFUNDED");
        long otherProgram = programId(createProgram(body(now.plus(Duration.ofDays(1)), 10, null)));
        long otherConfirmed = insertApplication(otherProgram, "CONFIRMED", now);
        insertPayment(otherConfirmed, "PAID");

        // when
        MvcTestResult result = post(ADMIN_PROGRAMS + "/" + programId + "/cancel");

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.programId").isEqualTo((int) programId);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("CANCELED");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.canceledApplicationCount")
                .isEqualTo(2);
        assertThat(statusOfProgram(programId)).isEqualTo("CANCELED");
        assertThat(applicationStatus(pending)).isEqualTo("CANCELED");
        assertThat(applicationStatus(confirmed)).isEqualTo("CANCELED");
        assertThat(cancelReason(pending)).isEqualTo("PROGRAM_CANCELED");
        assertThat(cancelReason(confirmed)).isEqualTo("PROGRAM_CANCELED");
        assertThat(count("program_application WHERE id = " + pending + " AND canceled_at = '2026-10-05 03:00:00'"))
                .isEqualTo(1);
        assertThat(applicationStatus(expired)).isEqualTo("EXPIRED");
        assertThat(cancelReason(expired)).isNull();
        assertThat(applicationStatus(canceledByUser)).isEqualTo("CANCELED");
        assertThat(cancelReason(canceledByUser)).isEqualTo("USER");
        assertThat(paymentStatus(confirmed)).isEqualTo("REFUNDED");
        assertThat(count("payment WHERE program_application_id = " + confirmed
                        + " AND refunded_at = '2026-10-05 03:00:00'"))
                .isEqualTo(1);
        assertThat(paymentStatus(otherConfirmed)).isEqualTo("PAID");
        assertThat(applicationStatus(otherConfirmed)).isEqualTo("CONFIRMED");
        MvcTestResult again = post(ADMIN_PROGRAMS + "/" + programId + "/cancel");
        assertThat(again).hasStatus(HttpStatus.CONFLICT);
        assertThat(again).bodyJson().extractingPath("$.code").isEqualTo("PROGRAM_INVALID_STATE");
        MvcTestResult revise = patch(programId, "{\"fee\":0}");
        assertThat(revise).hasStatus(HttpStatus.CONFLICT);
        assertThat(revise).bodyJson().extractingPath("$.code").isEqualTo("PROGRAM_INVALID_STATE");
        assertThat(post(ADMIN_PROGRAMS + "/999999/cancel")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("[F-17] 공개 목록은 status로 거르고 시작 시각 순서로 나오며, 남은 자리를 계산하고 취소된 행사를 뺀다")
    void publicListFiltersAndCountsRemainingSeats() {
        // given
        Instant now = clock.instant();
        long open = programId(createProgram(bodyWithStart(
                now.minus(Duration.ofHours(1)), now.plus(Duration.ofDays(5)), now.plus(Duration.ofDays(20)), 10)));
        long upcoming = programId(createProgram(bodyWithStart(
                now.plus(Duration.ofDays(1)), now.plus(Duration.ofDays(5)), now.plus(Duration.ofDays(10)), 10)));
        long closed = programId(createProgram(bodyWithStart(
                now.minus(Duration.ofDays(3)), now.plus(Duration.ofHours(1)), now.plus(Duration.ofDays(3)), 10)));
        long canceled = programId(createProgram(bodyWithStart(
                now.minus(Duration.ofHours(1)), now.plus(Duration.ofDays(5)), now.plus(Duration.ofDays(7)), 10)));
        assertThat(post(ADMIN_PROGRAMS + "/" + canceled + "/cancel")).hasStatus(HttpStatus.OK);
        clock.advance(Duration.ofHours(2));
        insertApplication(open, "PENDING_PAYMENT", now);
        insertApplication(open, "CONFIRMED", now);
        insertApplication(open, "EXPIRED", now);
        insertApplication(open, "CANCELED", now);

        // when
        MvcTestResult all = mvc.get().uri(PROGRAMS).exchange();
        MvcTestResult onlyOpen = mvc.get().uri(PROGRAMS + "?status=OPEN").exchange();
        MvcTestResult onlyUpcoming =
                mvc.get().uri(PROGRAMS + "?status=UPCOMING").exchange();
        MvcTestResult onlyClosed = mvc.get().uri(PROGRAMS + "?status=CLOSED").exchange();
        MvcTestResult paged = mvc.get().uri(PROGRAMS + "?size=1&page=1").exchange();

        // then
        assertThat(ids(all, "$.content[*].programId")).containsExactly(closed, upcoming, open);
        assertThat(ids(onlyOpen, "$.content[*].programId")).containsExactly(open);
        assertThat(ids(onlyUpcoming, "$.content[*].programId")).containsExactly(upcoming);
        assertThat(ids(onlyClosed, "$.content[*].programId")).containsExactly(closed);
        assertThat(onlyOpen)
                .bodyJson()
                .extractingPath("$.content[0].remainingSeats")
                .isEqualTo(8);
        assertThat(onlyOpen).bodyJson().extractingPath("$.content[0].status").isEqualTo("OPEN");
        assertThat(onlyOpen).bodyJson().extractingPath("$.content[0].capacity").isEqualTo(10);
        assertThat(onlyUpcoming)
                .bodyJson()
                .extractingPath("$.content[0].remainingSeats")
                .isEqualTo(10);
        assertThat(ids(paged, "$.content[*].programId")).containsExactly(upcoming);
        assertThat(paged).bodyJson().extractingPath("$.hasNext").isEqualTo(true);
        MvcTestResult canceledDetail = mvc.get().uri(PROGRAMS + "/" + canceled).exchange();
        assertThat(canceledDetail).hasStatus(HttpStatus.OK);
        assertThat(canceledDetail).bodyJson().extractingPath("$.status").isEqualTo("CANCELED");
        assertThat(mvc.get().uri(PROGRAMS + "/999999").exchange()).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("[F-17] 상세는 로그인한 회원에게 가장 최근 신청을 myApplication으로 주고, 신청이 없으면 필드를 뺀다")
    void detailShowsLatestApplicationOfViewer() {
        // given
        Instant now = clock.instant();
        long programId = programId(createProgram(body(now.minus(Duration.ofHours(1)), 10, null)));
        long viewerId = fixture.saveVerifiedMember(TestSequence.nickname());
        Cookie viewer = login(viewerId);
        Cookie other = login(fixture.saveVerifiedMember(TestSequence.nickname()));
        insertApplicationFor(programId, viewerId, "CANCELED", now.minus(Duration.ofHours(1)));
        long latest = insertApplicationFor(programId, viewerId, "PENDING_PAYMENT", now.plus(Duration.ofMinutes(15)));

        // when
        MvcTestResult mine =
                mvc.get().uri(PROGRAMS + "/" + programId).cookie(viewer).exchange();
        MvcTestResult others =
                mvc.get().uri(PROGRAMS + "/" + programId).cookie(other).exchange();

        // then
        assertThat(mine).hasStatus(HttpStatus.OK);
        assertThat(mine)
                .bodyJson()
                .extractingPath("$.myApplication.applicationId")
                .isEqualTo((int) latest);
        assertThat(mine).bodyJson().extractingPath("$.myApplication.status").isEqualTo("PENDING_PAYMENT");
        assertThat(mine)
                .bodyJson()
                .extractingPath("$.myApplication.paymentDueAt")
                .isEqualTo(now.plus(Duration.ofMinutes(15)).toString());
        assertThat(mine).bodyJson().extractingPath("$.remainingSeats").isEqualTo(9);
        assertThat(others).hasStatus(HttpStatus.OK);
        assertThat(others).bodyJson().doesNotHavePath("$.myApplication");
    }

    @Test
    @DisplayName("[F-17] 일반 회원이 행사 관리 API를 부르면 403이고 행사는 바뀌지 않으며 감사 로그도 없다")
    void memberCannotManagePrograms() {
        // given
        long programId = programId(createProgram(body(clock.instant().plus(Duration.ofDays(1)), 10, null)));
        jdbc.update("DELETE FROM admin_audit_log");
        Cookie member = login(fixture.saveVerifiedMember(TestSequence.nickname()));

        // when
        MvcTestResult create = send(
                mvc.post().uri(ADMIN_PROGRAMS), member, body(clock.instant().plus(Duration.ofDays(1)), 10, null));
        MvcTestResult revise = send(mvc.patch().uri(ADMIN_PROGRAMS + "/" + programId), member, "{\"fee\":0}");
        MvcTestResult cancel = mvc.post()
                .uri(ADMIN_PROGRAMS + "/" + programId + "/cancel")
                .cookie(member)
                .with(csrf())
                .exchange();
        MvcTestResult list = mvc.get()
                .uri(ADMIN_PROGRAMS + "/" + programId + "/applications")
                .cookie(member)
                .exchange();

        // then
        for (MvcTestResult result : List.of(create, revise, cancel, list)) {
            assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        }
        assertThat(count("program")).isEqualTo(1);
        assertThat(statusOfProgram(programId)).isEqualTo("SCHEDULED");
        assertThat(count("admin_audit_log")).isZero();
    }

    @Test
    @DisplayName("[F-17][F-21] 등록, 수정, 취소마다 감사 로그가 한 줄씩 남고 제목·설명 원문은 담지 않는다")
    void recordsAuditLogForEachAction() {
        // given
        Instant now = clock.instant();
        long programId = programId(createProgram(body(now.minus(Duration.ofHours(1)), 100, null)));
        insertApplication(programId, "CONFIRMED", now);

        // when
        assertThat(patch(programId, "{\"title\":\"새 제목\",\"capacity\":120}")).hasStatus(HttpStatus.OK);
        assertThat(patch(programId, "{\"fee\":5000}")).hasStatus(HttpStatus.OK);
        assertThat(post(ADMIN_PROGRAMS + "/" + programId + "/cancel")).hasStatus(HttpStatus.OK);

        // then
        assertThat(count("admin_audit_log WHERE action = 'PROGRAM_CREATE'")).isEqualTo(1);
        assertThat(count("admin_audit_log WHERE action = 'PROGRAM_UPDATE'")).isEqualTo(2);
        assertThat(count("admin_audit_log WHERE action = 'PROGRAM_CANCEL'")).isEqualTo(1);
        assertThat(count("admin_audit_log WHERE admin_id = " + adminId + " AND target_type = 'PROGRAM'"
                        + " AND target_id = " + programId))
                .isEqualTo(4);
        String create = auditDetail("PROGRAM_CREATE");
        assertThat((Integer) JsonPath.read(create, "$.capacity")).isEqualTo(100);
        assertThat((Boolean) JsonPath.read(create, "$.overnight")).isTrue();
        List<String> updates = jdbc.queryForList(
                "SELECT detail FROM admin_audit_log WHERE action = 'PROGRAM_UPDATE' ORDER BY id", String.class);
        assertThat((List<String>) JsonPath.read(updates.get(0), "$.changedFields"))
                .containsExactly("title", "capacity");
        assertThat((Integer) JsonPath.read(updates.get(0), "$.capacityBefore")).isEqualTo(100);
        assertThat((Integer) JsonPath.read(updates.get(0), "$.capacityAfter")).isEqualTo(120);
        assertThat((List<String>) JsonPath.read(updates.get(1), "$.changedFields"))
                .containsExactly("fee");
        assertThat(updates.get(1)).doesNotContain("capacityBefore").doesNotContain("capacityAfter");
        String cancel = auditDetail("PROGRAM_CANCEL");
        assertThat((String) JsonPath.read(cancel, "$.fromStatus")).isEqualTo("SCHEDULED");
        assertThat((String) JsonPath.read(cancel, "$.toStatus")).isEqualTo("CANCELED");
        assertThat((Integer) JsonPath.read(cancel, "$.canceledApplicationCount"))
                .isEqualTo(1);
        assertThat((Integer) JsonPath.read(cancel, "$.refundedPaymentCount")).isZero();
        String all = String.join(
                "",
                jdbc.queryForList("SELECT detail FROM admin_audit_log WHERE target_type = 'PROGRAM'", String.class));
        assertThat(all).doesNotContain("새 제목").doesNotContain("가을 백패킹").doesNotContain("설명");
    }

    private String body(Instant applyOpenAt, int capacity, Long spotId) {
        Instant now = clock.instant();
        return bodyFull(
                applyOpenAt,
                now.plus(Duration.ofDays(10)),
                now.plus(Duration.ofDays(14)),
                now.plus(Duration.ofDays(15)),
                capacity,
                spotId);
    }

    private String bodyWithStart(Instant applyOpenAt, Instant applyCloseAt, Instant startAt, int capacity) {
        return bodyFull(applyOpenAt, applyCloseAt, startAt, startAt.plus(Duration.ofDays(1)), capacity, null);
    }

    private static String bodyFull(
            Instant applyOpenAt, Instant applyCloseAt, Instant startAt, Instant endAt, int capacity, Long spotId) {
        return """
                {"title":"가을 백패킹","description":"설명","spotId":%s,"locationText":"설악산 입구",\
                "startAt":"%s","endAt":"%s","capacity":%d,"fee":30000,\
                "applyOpenAt":"%s","applyCloseAt":"%s","overnight":true}""".formatted(spotId, startAt, endAt, capacity, applyOpenAt, applyCloseAt);
    }

    private MvcTestResult createProgram(String body) {
        return send(mvc.post().uri(ADMIN_PROGRAMS), adminSession, body);
    }

    private MvcTestResult patch(long programId, String body) {
        return send(mvc.patch().uri(ADMIN_PROGRAMS + "/" + programId), adminSession, body);
    }

    private MvcTestResult send(MockMvcTester.MockMvcRequestBuilder builder, Cookie session, String body) {
        return builder.cookie(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult get(String uri) {
        return mvc.get().uri(uri).cookie(adminSession).exchange();
    }

    private MvcTestResult post(String uri) {
        return mvc.post().uri(uri).cookie(adminSession).with(csrf()).exchange();
    }

    private long insertSpot(String status) {
        String name = TestSequence.unique("장소");
        jdbc.update(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                        + " VALUES ('CAMPSITE', ?, ST_SRID(POINT(127.3, 37.3), 4326), 60, 127, ?, ?, ?)",
                name,
                status,
                clock.instant(),
                clock.instant());
        return jdbc.queryForObject("SELECT id FROM spot WHERE name = ?", Long.class, name);
    }

    // 신청마다 새 회원이 신청한 것으로 만든다. 신청 ID를 돌려준다.
    private long insertApplication(long programId, String status, Instant now) {
        long memberId = fixture.saveVerifiedMember(TestSequence.nickname());
        return insertApplicationFor(programId, memberId, status, now.plus(Duration.ofMinutes(15)));
    }

    private long insertApplicationFor(long programId, long memberId, String status, Instant paymentDueAt) {
        jdbc.update(
                "INSERT INTO program_application"
                        + " (program_id, member_id, status, payment_due_at, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                programId,
                memberId,
                status,
                paymentDueAt,
                clock.instant(),
                clock.instant());
        return jdbc.queryForObject(
                "SELECT MAX(id) FROM program_application WHERE program_id = ? AND member_id = ?",
                Long.class,
                programId,
                memberId);
    }

    private void insertPayment(long applicationId, String status) {
        jdbc.update(
                "INSERT INTO payment (program_application_id, amount, status, paid_at, created_at, updated_at)"
                        + " VALUES (?, 30000, ?, ?, ?, ?)",
                applicationId,
                status,
                clock.instant(),
                clock.instant(),
                clock.instant());
    }

    private long memberIdOf(long applicationId) {
        return jdbc.queryForObject("SELECT member_id FROM program_application WHERE id = ?", Long.class, applicationId);
    }

    private String applicationStatus(long applicationId) {
        return jdbc.queryForObject("SELECT status FROM program_application WHERE id = ?", String.class, applicationId);
    }

    private String cancelReason(long applicationId) {
        return jdbc.queryForObject(
                "SELECT cancel_reason FROM program_application WHERE id = ?", String.class, applicationId);
    }

    private String paymentStatus(long applicationId) {
        return jdbc.queryForObject(
                "SELECT status FROM payment WHERE program_application_id = ?", String.class, applicationId);
    }

    private String statusOfProgram(long programId) {
        return jdbc.queryForObject("SELECT status FROM program WHERE id = ?", String.class, programId);
    }

    private int capacityOf(long programId) {
        return jdbc.queryForObject("SELECT capacity FROM program WHERE id = ?", Integer.class, programId);
    }

    private String auditDetail(String action) {
        return jdbc.queryForObject("SELECT detail FROM admin_audit_log WHERE action = ?", String.class, action);
    }

    private int count(String tableAndCondition) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tableAndCondition, Integer.class);
    }

    private static long programId(MvcTestResult result) {
        Number id = JsonPath.read(
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), "$.programId");
        return id.longValue();
    }

    private static List<Long> ids(MvcTestResult result, String path) {
        List<Number> numbers =
                JsonPath.read(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), path);
        return numbers.stream().map(Number::longValue).toList();
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
