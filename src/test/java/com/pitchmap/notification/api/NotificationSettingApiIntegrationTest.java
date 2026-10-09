package com.pitchmap.notification.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
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
class NotificationSettingApiIntegrationTest {

    private static final String PATH = "/api/me/notification-settings";
    private static final String PASSWORD = "Valid-pass1";
    private static final String SESSION_COOKIE = "SESSION";
    private static final int TYPE_COUNT = 12;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    private Member owner;
    private Cookie ownerSession;
    private Cookie otherSession;

    @BeforeEach
    void setUp() {
        owner = memberRepository.saveAndFlush(
                aMember().passwordHash(passwordEncoder.encode(PASSWORD)).build());
        Member other = memberRepository.saveAndFlush(
                aMember().passwordHash(passwordEncoder.encode(PASSWORD)).build());
        ownerSession = login(owner);
        otherSession = login(other);
    }

    @Test
    @DisplayName("[F-20] 설정이 없으면 모든 종류가 정해진 순서로 emailEnabled true이고, 이메일 인증 전 회원도 조회할 수 있다")
    void getReturnsDefaults() {
        MvcTestResult result = get(ownerSession);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(types(result))
                .hasSize(TYPE_COUNT)
                .startsWith("BASECAMP_APPLIED")
                .endsWith("PROGRAM_APPLICATION_CANCELED");
        assertThat(flags(result)).containsOnly(true);
    }

    @Test
    @DisplayName("[F-20] 설정을 통째로 바꾸면 응답과 이후 조회에 반영되고, 빠진 종류는 기본값으로 돌아가며 다른 회원은 그대로다")
    void putReplacesFullyAndOthersUnaffected() {
        // given
        put(
                ownerSession,
                "[{\"type\":\"BASECAMP_APPLIED\",\"emailEnabled\":false},"
                        + "{\"type\":\"BASECAMP_KICKED\",\"emailEnabled\":false}]");

        // when
        MvcTestResult second = put(ownerSession, "[{\"type\":\"BASECAMP_APPROVED\",\"emailEnabled\":false}]");

        // then
        assertThat(second).hasStatus(HttpStatus.OK);
        assertThat(disabledTypes(second)).containsExactly("BASECAMP_APPROVED");
        assertThat(disabledTypes(get(ownerSession))).containsExactly("BASECAMP_APPROVED");
        assertThat(get(otherSession)).hasStatus(HttpStatus.OK);
        assertThat(flags(get(otherSession))).containsOnly(true);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM notification_setting WHERE member_id = ?", Integer.class, owner.getId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("[F-20] 빈 배열로 바꾸면 모든 설정이 기본값으로 돌아간다")
    void emptyArrayResetsToDefaults() {
        put(ownerSession, "[{\"type\":\"BASECAMP_APPLIED\",\"emailEnabled\":false}]");

        MvcTestResult result = put(ownerSession, "[]");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(flags(result)).containsOnly(true);
    }

    @Test
    @DisplayName("[F-20] 알 수 없거나 중복된 종류는 400 INVALID_INPUT이고 기존 설정을 바꾸지 않는다")
    void invalidTypesAreRejectedWithoutChange() {
        // given
        put(ownerSession, "[{\"type\":\"BASECAMP_APPLIED\",\"emailEnabled\":false}]");

        // when
        MvcTestResult unknown = put(ownerSession, "[{\"type\":\"NO_SUCH\",\"emailEnabled\":false}]");
        MvcTestResult duplicate = put(
                ownerSession,
                "[{\"type\":\"BASECAMP_KICKED\",\"emailEnabled\":false},{\"type\":\"BASECAMP_KICKED\",\"emailEnabled\":true}]");

        // then
        assertThat(unknown).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(unknown).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(duplicate).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(disabledTypes(get(ownerSession))).containsExactly("BASECAMP_APPLIED");
    }

    @Test
    @DisplayName("[F-20] 로그인하지 않으면 401 AUTHENTICATION_REQUIRED다")
    void anonymousIsUnauthorized() {
        MvcTestResult result = mvc.get().uri(PATH).exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
    }

    private MvcTestResult get(Cookie session) {
        return mvc.get().uri(PATH).cookie(session).exchange();
    }

    private MvcTestResult put(Cookie session, String json) {
        return mvc.put()
                .uri(PATH)
                .cookie(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    private static String body(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private static List<String> types(MvcTestResult result) {
        return JsonPath.read(body(result), "$[*].type");
    }

    private static List<Boolean> flags(MvcTestResult result) {
        return JsonPath.read(body(result), "$[*].emailEnabled");
    }

    private static List<String> disabledTypes(MvcTestResult result) {
        return JsonPath.read(body(result), "$[?(@.emailEnabled == false)].type");
    }

    private Cookie login(Member member) {
        String body = "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(member.getEmail(), PASSWORD);
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
