package com.pitchmap.spot.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.application.EmailVerificationService;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
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
class BakjiApiIntegrationTest {

    private static final String PATH = "/api/bakjis";
    private static final String VALID_PASSWORD = "Valid-pass1";
    private static final String SESSION_COOKIE = "SESSION";
    private static final String LOCAL_IP = "127.0.0.1";

    // 경계는 경도 127.0~127.5, 위도 37.0~37.5인 사각형이다.
    private static final String PARK_SQUARE = "MULTIPOLYGON(((127 37, 127.5 37, 127.5 37.5, 127 37.5, 127 37)))";
    private static final String INSIDE_PARK_BODY =
            "{\"name\":\"능선 끝 평지\",\"lat\":37.25,\"lng\":127.25,\"hasWater\":true,\"hasToilet\":false}";

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
    @DisplayName("[F-07] 로그인하지 않고 제보하면 401 AUTHENTICATION_REQUIRED를 응답한다")
    void anonymousReportIsUnauthorized() {
        MvcTestResult result = mvc.post()
                .uri(PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(INSIDE_PARK_BODY)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    @DisplayName("[F-07][TR-03] 이메일 인증 전인 회원이 제보하면 403 MEMBER_NOT_VERIFIED를 응답하고 박지를 저장하지 않는다")
    void unverifiedMemberCannotReport() {
        Cookie session = login(saveMember());

        MvcTestResult result = send(mvc.post().uri(PATH), session, INSIDE_PARK_BODY);

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spot", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("[F-07] CSRF 토큰 없이 제보하면 403을 응답하고 박지를 저장하지 않는다")
    void reportWithoutCsrfIsForbidden() {
        Cookie session = verifiedSession(saveMember());

        MvcTestResult result = mvc.post()
                .uri(PATH)
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(INSIDE_PARK_BODY)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spot", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("[F-07] 인증 회원이 공원 경계 안 좌표로 제보하면 201과 함께 경고, 공원 이름, 안내 문구를 응답하고, 30m 옆 두 번째 제보는 첫 박지를 중복 후보로 받는다")
    void verifiedMemberReportsInsideBoundaryAndSeesDuplicate() {
        // given
        String areaName = TestSequence.unique("설악산");
        jdbc.update(
                "INSERT INTO protected_area (name, area_type, source, source_date, boundary, created_at, updated_at)"
                        + " VALUES (?, 'NATIONAL_PARK', 'KDPA', '2026-01-01',"
                        + " ST_GeomFromText(?, 4326, 'axis-order=long-lat'), NOW(6), NOW(6))",
                areaName,
                PARK_SQUARE);
        Cookie session = verifiedSession(saveMember());

        // when
        MvcTestResult first = send(mvc.post().uri(PATH), session, INSIDE_PARK_BODY);

        // then
        assertThat(first).hasStatus(HttpStatus.CREATED);
        int firstId = readSpotId(first);
        assertThat(first).bodyJson().extractingPath("$.parkWarning.warned").isEqualTo(true);
        assertThat(first).bodyJson().extractingPath("$.parkWarning.areaName").isEqualTo(areaName);
        assertThat(first).bodyJson().extractingPath("$.guide").isEqualTo("공원 안 지정 장소 밖 야영은 과태료 대상입니다. 흔적을 남기지 마세요.");
        assertThat(first)
                .bodyJson()
                .extractingPath("$.duplicateCandidates")
                .asList()
                .isEmpty();

        // when: 위도를 약 30m 북쪽으로 옮긴 좌표다(0.00027도).
        MvcTestResult second = send(
                mvc.post().uri(PATH),
                session,
                "{\"name\":\"옆 박지\",\"lat\":37.25027,\"lng\":127.25,\"hasWater\":false,\"hasToilet\":false}");

        // then
        assertThat(second).hasStatus(HttpStatus.CREATED);
        assertThat(second)
                .bodyJson()
                .extractingPath("$.duplicateCandidates")
                .asList()
                .hasSize(1);
        assertThat(second)
                .bodyJson()
                .extractingPath("$.duplicateCandidates[0].spotId")
                .isEqualTo(firstId);
        assertThat(second)
                .bodyJson()
                .extractingPath("$.duplicateCandidates[0].name")
                .isEqualTo("능선 끝 평지");
        assertThat(second)
                .bodyJson()
                .extractingPath("$.duplicateCandidates[0].distanceM")
                .isEqualTo(30);
    }

    @Test
    @DisplayName("[F-07] 기상청 격자 밖 좌표로 제보하면 400 INVALID_INPUT과 lat·lng 필드 오류를 응답한다")
    void reportOutsideWeatherGridReturnsFieldErrors() {
        Cookie session = verifiedSession(saveMember());

        MvcTestResult result = send(
                mvc.post().uri(PATH),
                session,
                "{\"name\":\"도쿄\",\"lat\":35.6762,\"lng\":139.6503,\"hasWater\":true,\"hasToilet\":true}");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field=='lat')]")
                .asList()
                .hasSize(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field=='lng')]")
                .asList()
                .hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spot", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("[F-07] 제보자가 수정하면 200과 함께 바뀐 값이 저장되고, 삭제하면 204 뒤에 장소 상세가 404가 된다")
    void reporterUpdatesAndDeletes() {
        // given
        Cookie session = verifiedSession(saveMember());
        int spotId = readSpotId(send(mvc.post().uri(PATH), session, INSIDE_PARK_BODY));

        // when
        MvcTestResult updated = send(
                mvc.patch().uri(PATH + "/" + spotId),
                session,
                "{\"name\":\"계곡 옆 평지\",\"hasToilet\":true,\"description\":\"물소리가 크다\"}");

        // then
        assertThat(updated).hasStatus(HttpStatus.OK);
        assertThat(updated).bodyJson().extractingPath("$.spotId").isEqualTo(spotId);
        assertThat(jdbc.queryForObject("SELECT name FROM spot WHERE id = ?", String.class, spotId))
                .isEqualTo("계곡 옆 평지");
        assertThat(jdbc.queryForObject("SELECT has_toilet FROM bakji_detail WHERE spot_id = ?", Boolean.class, spotId))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT description FROM bakji_detail WHERE spot_id = ?", String.class, spotId))
                .isEqualTo("물소리가 크다");

        // when
        MvcTestResult deleted = send(mvc.delete().uri(PATH + "/" + spotId), session, null);

        // then
        assertThat(deleted).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/spots/" + spotId).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        MvcTestResult again = send(mvc.delete().uri(PATH + "/" + spotId), session, null);
        assertThat(again).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(again).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("[F-07] 다른 회원이 수정하거나 삭제하면 403 ACCESS_DENIED를 응답하고 박지는 그대로다")
    void otherMemberCannotUpdateOrDelete() {
        // given
        Cookie reporter = verifiedSession(saveMember());
        Cookie stranger = verifiedSession(saveMember());
        int spotId = readSpotId(send(mvc.post().uri(PATH), reporter, INSIDE_PARK_BODY));

        // when
        MvcTestResult patched = send(mvc.patch().uri(PATH + "/" + spotId), stranger, "{\"name\":\"가로챔\"}");
        MvcTestResult deleted = send(mvc.delete().uri(PATH + "/" + spotId), stranger, null);

        // then
        assertThat(patched).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(patched).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        assertThat(deleted).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(deleted).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        assertThat(jdbc.queryForObject("SELECT name FROM spot WHERE id = ?", String.class, spotId))
                .isEqualTo("능선 끝 평지");
        assertThat(jdbc.queryForObject("SELECT status FROM spot WHERE id = ?", String.class, spotId))
                .isEqualTo("ACTIVE");
    }

    // 본문이 null이면 본문 없이 보낸다. 쓰기 요청이므로 항상 CSRF 토큰을 싣는다.
    private MvcTestResult send(MockMvcTester.MockMvcRequestBuilder builder, Cookie session, String body) {
        MockMvcTester.MockMvcRequestBuilder request =
                builder.with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            request = request.content(body);
        }
        return request.exchange();
    }

    private int readSpotId(MvcTestResult result) {
        assertThat(result).hasStatus(HttpStatus.CREATED);
        AtomicInteger spotId = new AtomicInteger();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.spotId")
                .asNumber()
                .satisfies(number -> spotId.set(number.intValue()));
        return spotId.get();
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
