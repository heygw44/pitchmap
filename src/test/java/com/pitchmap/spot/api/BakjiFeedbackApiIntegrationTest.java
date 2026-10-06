package com.pitchmap.spot.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.application.EmailVerificationService;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
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
class BakjiFeedbackApiIntegrationTest {

    private static final String PATH = "/api/bakjis";
    private static final String VALID_PASSWORD = "Valid-pass1";
    private static final String SESSION_COOKIE = "SESSION";
    private static final String LOCAL_IP = "127.0.0.1";
    private static final String BAKJI_BODY =
            "{\"name\":\"능선 끝 평지\",\"lat\":37.25,\"lng\":127.25,\"hasWater\":true,\"hasToilet\":false}";
    private static final String REPORT_BODY = "{\"reason\":\"FALSE_INFO\",\"content\":\"물이 없다\"}";
    private static final int REPORTS_TO_PENDING_REVIEW = 5;

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

    @Test
    @DisplayName("[F-08] 회원이 박지를 확인하면 201과 확인 수 1을 응답하고, 같은 회원의 두 번째 확인은 409 BAKJI_ALREADY_CONFIRMED이며, 상세의 확인 수에 반영된다")
    void memberConfirmsOnceAndDetailShowsCount() {
        // given
        int spotId = createBakji(verifiedSession(saveMember()));
        Cookie confirmer = verifiedSession(saveMember());

        // when
        MvcTestResult first = post(confirmer, PATH + "/" + spotId + "/confirmations", null);
        MvcTestResult second = post(confirmer, PATH + "/" + spotId + "/confirmations", null);

        // then
        assertThat(first).hasStatus(HttpStatus.CREATED);
        assertThat(first).bodyJson().extractingPath("$.confirmationCount").isEqualTo(1);
        assertThat(second).hasStatus(HttpStatus.CONFLICT);
        assertThat(second).bodyJson().extractingPath("$.code").isEqualTo("BAKJI_ALREADY_CONFIRMED");
        assertThat(count("SELECT COUNT(*) FROM bakji_confirmation WHERE spot_id = ?", spotId))
                .isEqualTo(1);
        MvcTestResult detail = mvc.get().uri("/api/spots/" + spotId).exchange();
        assertThat(detail).hasStatus(HttpStatus.OK);
        assertThat(detail)
                .bodyJson()
                .extractingPath("$.bakji.confirmationCount")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("[F-08] 다른 회원이 확인하면 확인 수가 2가 된다")
    void confirmationCountGrowsWithMembers() {
        // given
        int spotId = createBakji(verifiedSession(saveMember()));
        post(verifiedSession(saveMember()), PATH + "/" + spotId + "/confirmations", null);

        // when
        MvcTestResult result = post(verifiedSession(saveMember()), PATH + "/" + spotId + "/confirmations", null);

        // then
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.confirmationCount").isEqualTo(2);
    }

    @Test
    @DisplayName("[F-08] 회원이 박지를 신고하면 201을 본문 없이 응답하고 사유와 내용을 저장하며, 같은 회원의 두 번째 신고는 409 BAKJI_ALREADY_REPORTED다")
    void memberReportsOnce() {
        // given
        int spotId = createBakji(verifiedSession(saveMember()));
        Cookie reporter = verifiedSession(saveMember());

        // when
        MvcTestResult first = post(reporter, PATH + "/" + spotId + "/reports", REPORT_BODY);
        MvcTestResult second = post(reporter, PATH + "/" + spotId + "/reports", REPORT_BODY);

        // then
        assertThat(first).hasStatus(HttpStatus.CREATED);
        assertThat(first).body().isEmpty();
        assertThat(second).hasStatus(HttpStatus.CONFLICT);
        assertThat(second).bodyJson().extractingPath("$.code").isEqualTo("BAKJI_ALREADY_REPORTED");
        assertThat(count("SELECT COUNT(*) FROM bakji_report WHERE spot_id = ?", spotId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT reason FROM bakji_report WHERE spot_id = ?", String.class, spotId))
                .isEqualTo("FALSE_INFO");
        assertThat(jdbc.queryForObject("SELECT content FROM bakji_report WHERE spot_id = ?", String.class, spotId))
                .isEqualTo("물이 없다");
        assertThat(statusOf(spotId)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("[F-08] 신고가 4건까지는 박지가 ACTIVE이고, 5번째 신고에 PENDING_REVIEW가 되어 지도 영역 조회와 상세에서 빠지며 더 신고하면 404다")
    void fifthReportMovesBakjiToPendingReview() {
        // given
        int spotId = createBakji(verifiedSession(saveMember()));

        // when
        for (int i = 1; i < REPORTS_TO_PENDING_REVIEW; i++) {
            assertThat(post(verifiedSession(saveMember()), PATH + "/" + spotId + "/reports", REPORT_BODY))
                    .hasStatus(HttpStatus.CREATED);
        }

        // then
        assertThat(statusOf(spotId)).isEqualTo("ACTIVE");
        assertThat(markerCount()).isEqualTo(1);

        // when
        Cookie fifth = verifiedSession(saveMember());
        MvcTestResult result = post(fifth, PATH + "/" + spotId + "/reports", REPORT_BODY);

        // then
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(statusOf(spotId)).isEqualTo("PENDING_REVIEW");
        assertThat(markerCount()).isZero();
        assertThat(mvc.get().uri("/api/spots/" + spotId).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        MvcTestResult sixth = post(verifiedSession(saveMember()), PATH + "/" + spotId + "/reports", REPORT_BODY);
        assertThat(sixth).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(sixth).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(count("SELECT COUNT(*) FROM bakji_report WHERE spot_id = ?", spotId))
                .isEqualTo(REPORTS_TO_PENDING_REVIEW);
    }

    @Test
    @DisplayName("[F-08] 없는 장소, 공공데이터 장소, 삭제됐거나 검토 대기인 박지를 확인하거나 신고하면 404 NOT_FOUND를 응답한다")
    void unavailableTargetsAreNotFound() {
        // given
        Cookie member = verifiedSession(saveMember());
        Cookie reporter = verifiedSession(saveMember());
        long campsiteId = insertCampsite();
        int deletedId = createBakji(reporter);
        int pendingId = createBakji(reporter);
        jdbc.update("UPDATE spot SET status = 'DELETED' WHERE id = ?", deletedId);
        jdbc.update("UPDATE spot SET status = 'PENDING_REVIEW' WHERE id = ?", pendingId);

        // when & then
        for (long spotId : new long[] {9_999_999L, campsiteId, deletedId, pendingId}) {
            MvcTestResult confirm = post(member, PATH + "/" + spotId + "/confirmations", null);
            MvcTestResult report = post(member, PATH + "/" + spotId + "/reports", REPORT_BODY);

            assertThat(confirm).hasStatus(HttpStatus.NOT_FOUND);
            assertThat(confirm).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
            assertThat(report).hasStatus(HttpStatus.NOT_FOUND);
            assertThat(report).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        }
        assertThat(count("SELECT COUNT(*) FROM bakji_confirmation")).isZero();
        assertThat(count("SELECT COUNT(*) FROM bakji_report")).isZero();
    }

    @Test
    @DisplayName("[F-08] 제보자가 자기 박지를 확인하거나 신고하는 것은 막지 않는다")
    void reporterMayConfirmAndReportOwnBakji() {
        // given
        Cookie reporter = verifiedSession(saveMember());
        int spotId = createBakji(reporter);

        // when
        MvcTestResult confirm = post(reporter, PATH + "/" + spotId + "/confirmations", null);
        MvcTestResult report = post(reporter, PATH + "/" + spotId + "/reports", REPORT_BODY);

        // then
        assertThat(confirm).hasStatus(HttpStatus.CREATED);
        assertThat(report).hasStatus(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("[F-08][TR-03] 이메일 인증 전인 회원이 확인하거나 신고하면 403 MEMBER_NOT_VERIFIED를 응답하고, 로그인하지 않으면 401이다")
    void unverifiedAndAnonymousCannotConfirmOrReport() {
        // given
        int spotId = createBakji(verifiedSession(saveMember()));
        Cookie unverified = login(saveMember());

        // when
        MvcTestResult confirm = post(unverified, PATH + "/" + spotId + "/confirmations", null);
        MvcTestResult report = post(unverified, PATH + "/" + spotId + "/reports", REPORT_BODY);
        MvcTestResult anonymous = mvc.post()
                .uri(PATH + "/" + spotId + "/confirmations")
                .with(csrf())
                .exchange();

        // then
        assertThat(confirm).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(confirm).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        assertThat(report).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(report).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        assertThat(anonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(count("SELECT COUNT(*) FROM bakji_confirmation")).isZero();
        assertThat(count("SELECT COUNT(*) FROM bakji_report")).isZero();
    }

    @Test
    @DisplayName("[F-08] 모르는 신고 사유로 신고하면 400 INVALID_INPUT을 응답하고 신고를 저장하지 않는다")
    void unknownReasonIsInvalidInput() {
        // given
        int spotId = createBakji(verifiedSession(saveMember()));
        Cookie reporter = verifiedSession(saveMember());

        // when
        MvcTestResult result = post(reporter, PATH + "/" + spotId + "/reports", "{\"reason\":\"SPAM\"}");

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(count("SELECT COUNT(*) FROM bakji_report")).isZero();
    }

    private int createBakji(Cookie session) {
        MvcTestResult created = post(session, PATH, BAKJI_BODY);
        assertThat(created).hasStatus(HttpStatus.CREATED);
        AtomicInteger spotId = new AtomicInteger();
        assertThat(created)
                .bodyJson()
                .extractingPath("$.spotId")
                .asNumber()
                .satisfies(number -> spotId.set(number.intValue()));
        return spotId.get();
    }

    // 본문이 null이면 본문 없이 보낸다. 쓰기 요청이므로 항상 CSRF 토큰을 싣는다.
    private MvcTestResult post(Cookie session, String uri, String body) {
        MockMvcTester.MockMvcRequestBuilder request =
                mvc.post().uri(uri).with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            request = request.content(body);
        }
        return request.exchange();
    }

    private int markerCount() {
        MvcTestResult result = mvc.get()
                .uri("/api/spots")
                .param("swLat", "37")
                .param("swLng", "127")
                .param("neLat", "38")
                .param("neLng", "128")
                .param("zoom", "12")
                .param("width", "360")
                .param("height", "740")
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        AtomicInteger size = new AtomicInteger();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.markers")
                .asList()
                .satisfies(markers -> size.set(markers.size()));
        return size.get();
    }

    private String statusOf(long spotId) {
        return jdbc.queryForObject("SELECT status FROM spot WHERE id = ?", String.class, spotId);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private long insertCampsite() {
        Instant now = MutableClock.DEFAULT_INSTANT;
        String name = TestSequence.unique("야영장");
        jdbc.update(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                        + " VALUES ('CAMPSITE', ?, ST_SRID(POINT(127.3, 37.3), 4326), 60, 127, 'ACTIVE', ?, ?)",
                name,
                now,
                now);
        return jdbc.queryForObject("SELECT id FROM spot WHERE name = ?", Long.class, name);
    }

    private Member saveMember() {
        return memberRepository.save(aMember()
                .email(TestSequence.email())
                .passwordHash(passwordEncoder.encode(VALID_PASSWORD))
                .build());
    }

    private Cookie login(Member member) {
        String body = "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(member.getEmail(), VALID_PASSWORD);
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

    // 실제 흐름대로 로그인한 세션에서 인증 코드를 확인해야 그 세션이 이메일 인증 권한을 얻는다.
    // 코드 원값은 메일로만 나가므로, 테스트는 발송 처리기가 쓰는 서비스를 직접 불러 코드를 받는다.
    private Cookie verifiedSession(Member member) {
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
        return session;
    }
}
