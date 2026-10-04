package com.pitchmap.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.trace.TraceIdFilter;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@AutoConfigureMockMvc
class SecurityConfigIntegrationTest {

    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";
    private static final String SESSION_COOKIE = "SESSION";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationContext applicationContext;

    static Stream<Arguments> publicEndpoints() {
        return Stream.of(
                Arguments.of(HttpMethod.POST, "/api/members"),
                Arguments.of(HttpMethod.POST, "/api/auth/login"),
                Arguments.of(HttpMethod.POST, "/api/auth/password-reset/request"),
                Arguments.of(HttpMethod.POST, "/api/auth/password-reset/confirm"),
                Arguments.of(HttpMethod.GET, "/api/members/1/profile"),
                Arguments.of(HttpMethod.GET, "/api/spots"),
                Arguments.of(HttpMethod.GET, "/api/spots/nearby"),
                Arguments.of(HttpMethod.GET, "/api/spots/1"),
                Arguments.of(HttpMethod.GET, "/api/spots/1/reviews"),
                Arguments.of(HttpMethod.GET, "/api/basecamps"),
                Arguments.of(HttpMethod.GET, "/api/basecamps/1"),
                Arguments.of(HttpMethod.GET, "/api/programs"),
                Arguments.of(HttpMethod.GET, "/api/programs/1"));
    }

    // 로그인해야 하는 행, 그리고 공개 경로와 경로는 같지만 HTTP 메서드가 다른 행이다.
    static Stream<Arguments> loginRequiredEndpoints() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/me"),
                Arguments.of(HttpMethod.DELETE, "/api/me"),
                Arguments.of(HttpMethod.GET, "/api/me/basecamps"),
                Arguments.of(HttpMethod.POST, "/api/auth/logout"),
                Arguments.of(HttpMethod.POST, "/api/bakjis"),
                Arguments.of(HttpMethod.POST, "/api/spots/1/reviews"),
                Arguments.of(HttpMethod.POST, "/api/basecamps"),
                Arguments.of(HttpMethod.GET, "/api/members"),
                Arguments.of(HttpMethod.POST, "/api/spots"),
                Arguments.of(HttpMethod.DELETE, "/api/programs/1"));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("publicEndpoints")
    @DisplayName("[ADR-002] 공개 엔드포인트는 로그인하지 않아도 인증·권한 오류 없이 컨트롤러까지 간다")
    void publicEndpointsDoNotRequireLogin(HttpMethod method, String path) {
        String csrfToken = issueCsrfToken();

        MvcTestResult result = withCsrf(mvc.method(method).uri(path), csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .exchange();

        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("loginRequiredEndpoints")
    @DisplayName("[ADR-002] 로그인이 필요한 엔드포인트는 로그인하지 않으면 401 AUTHENTICATION_REQUIRED로 응답한다")
    void loginRequiredEndpointsReturnUnauthorizedWithoutLogin(HttpMethod method, String path) {
        String csrfToken = issueCsrfToken();

        MvcTestResult result = withCsrf(mvc.method(method).uri(path), csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    @DisplayName("[ADR-002] 401 응답은 오류 응답 JSON이고 traceId가 X-Trace-Id 헤더와 같다")
    void unauthorizedResponseHasErrorBodyWithTraceId() {
        MvcTestResult result = mvc.get().uri("/api/me").exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(result).bodyJson().extractingPath("$.message").isEqualTo("로그인이 필요합니다.");
        assertThat(result).bodyJson().doesNotHavePath("$.fieldErrors");
        String headerTraceId = result.getResponse().getHeader(TraceIdFilter.HEADER);
        assertThat(headerTraceId).isNotBlank();
        assertThat(result).bodyJson().extractingPath("$.traceId").isEqualTo(headerTraceId);
    }

    @Test
    @DisplayName("[ADR-002] 로그인하지 않고 관리자 경로를 부르면 401로 응답한다")
    void adminPathWithoutLoginReturnsUnauthorized() {
        MvcTestResult result = mvc.get().uri("/api/admin/audit-logs").exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    @DisplayName("[ADR-002] 공개 목록에 없고 /api도 아닌 경로는 로그인하지 않으면 401로 거부한다")
    void unlistedPathOutsideApiIsDenied() {
        MvcTestResult result = mvc.get().uri("/unlisted").exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    @DisplayName("[ADR-002] 헬스 체크는 로그인하지 않아도 200으로 응답한다")
    void healthEndpointIsPublic() {
        MvcTestResult result = mvc.get().uri("/actuator/health").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
    }

    @Test
    @DisplayName("[ADR-002] 첫 GET 응답에 JS가 읽을 수 있는 XSRF-TOKEN 쿠키를 내려준다")
    void firstGetIssuesReadableXsrfTokenCookie() {
        MvcTestResult result = mvc.get().uri("/actuator/health").exchange();

        Cookie cookie = result.getResponse().getCookie(CSRF_COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isNotBlank();
        assertThat(cookie.isHttpOnly()).isFalse();
    }

    @Test
    @DisplayName("[ADR-002] 쓰기 요청에 CSRF 토큰이 없으면 403 ACCESS_DENIED로 응답한다")
    void writeRequestWithoutCsrfTokenIsForbidden() {
        MvcTestResult result = mvc.post()
                .uri("/api/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        assertThat(result).bodyJson().extractingPath("$.traceId").asString().isNotBlank();
    }

    @Test
    @DisplayName("[ADR-002] 쿠키만 있고 X-XSRF-TOKEN 헤더가 없으면 403으로 응답한다")
    void writeRequestWithCookieButWithoutHeaderIsForbidden() {
        String csrfToken = issueCsrfToken();

        MvcTestResult result = mvc.post()
                .uri("/api/members")
                .cookie(new Cookie(CSRF_COOKIE, csrfToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
    }

    @Test
    @DisplayName("[ADR-002] 헤더의 CSRF 토큰이 쿠키와 다르면 403으로 응답한다")
    void writeRequestWithMismatchedCsrfTokenIsForbidden() {
        String csrfToken = issueCsrfToken();

        MvcTestResult result = mvc.post()
                .uri("/api/members")
                .cookie(new Cookie(CSRF_COOKIE, csrfToken))
                .header(CSRF_HEADER, "forged-" + csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
    }

    @Test
    @DisplayName("[ADR-002] 쿠키와 헤더에 같은 CSRF 토큰을 보내면 요청이 컨트롤러까지 간다")
    void writeRequestWithMatchingCsrfTokenReachesController() {
        String csrfToken = issueCsrfToken();

        MvcTestResult result = withCsrf(mvc.post().uri("/api/members"), csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"password\":\"x\",\"nickname\":\"x\"}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
    }

    @Test
    @DisplayName("[ADR-002] 읽기 요청은 CSRF 토큰 없이도 허용한다")
    void readRequestDoesNotNeedCsrfToken() {
        MvcTestResult result = mvc.get().uri("/api/spots").exchange();

        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    @Test
    @DisplayName("[ADR-002] 로그인하면 SESSION 쿠키를 HttpOnly, Secure, SameSite=Lax로 내려준다")
    void loginIssuesHardenedSessionCookie() {
        MvcTestResult result = login(7L, "USER");

        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        String setCookie = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith(SESSION_COOKIE + "="))
                .findFirst()
                .orElseThrow();
        assertThat(setCookie)
                .containsIgnoringCase("HttpOnly")
                .containsIgnoringCase("Secure")
                .containsIgnoringCase("SameSite=Lax");
    }

    @Test
    @DisplayName("[ADR-002] 로그인하면 세션 테이블의 principal 이름이 회원 ID가 된다")
    void loginStoresMemberIdAsSessionPrincipalName() {
        login(42L, "USER");

        String principalName = jdbcTemplate.queryForObject("SELECT PRINCIPAL_NAME FROM SPRING_SESSION", String.class);
        assertThat(principalName).isEqualTo("42");
    }

    @Test
    @DisplayName("[ADR-002] 로그인한 회원은 로그인 세션 쿠키로 로그인 필요 경로를 통과한다")
    void loggedInMemberPassesLoginRequiredPath() {
        Cookie session = loginAndGetSession(7L, "USER");

        MvcTestResult result = mvc.get().uri("/api/me").cookie(session).exchange();

        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    @Test
    @DisplayName("[ADR-002] 일반 회원이 관리자 경로를 부르면 403 ACCESS_DENIED로 응답한다")
    void userRoleCannotUseAdminPath() {
        Cookie session = loginAndGetSession(7L, "USER");

        MvcTestResult result =
                mvc.get().uri("/api/admin/audit-logs").cookie(session).exchange();

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        assertThat(result).bodyJson().extractingPath("$.traceId").asString().isNotBlank();
    }

    @Test
    @DisplayName("[ADR-002] 관리자 회원은 관리자 경로를 통과한다")
    void adminRoleCanUseAdminPath() {
        Cookie session = loginAndGetSession(1L, "ADMIN");

        MvcTestResult result =
                mvc.get().uri("/api/admin/audit-logs").cookie(session).exchange();

        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    @Test
    @DisplayName("[ADR-002] 로그인 전 세션이 있으면 로그인할 때 세션 ID를 바꾸고 이전 세션을 지운다")
    void loginReplacesSessionIdOfExistingSession() {
        Cookie before = createSession();

        MvcTestResult result = mvc.post()
                .uri("/security-test/login")
                .param("memberId", "7")
                .param("role", "USER")
                .cookie(before)
                .exchange();

        Cookie after = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(after).isNotNull();
        assertThat(after.getValue()).isNotEqualTo(before.getValue());
        assertThat(jdbcTemplate.queryForList("SELECT SESSION_ID FROM SPRING_SESSION", String.class))
                .containsExactly(decodeSessionId(after));
    }

    @Test
    @DisplayName("[ADR-002] 로그아웃하면 세션이 지워지고 같은 세션 쿠키로는 로그인 필요 경로를 쓸 수 없다")
    void logoutInvalidatesSession() {
        Cookie session = loginAndGetSession(7L, "USER");

        MvcTestResult logout =
                mvc.post().uri("/security-test/logout").cookie(session).exchange();
        MvcTestResult afterLogout = mvc.get().uri("/api/me").cookie(session).exchange();

        assertThat(logout).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION", Long.class))
                .isZero();
        assertThat(afterLogout).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(afterLogout).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    @DisplayName("[ADR-002] 사용자 조회 빈이 없어서 Spring Boot가 임시 비밀번호 사용자를 만들지 않는다")
    void noGeneratedUserDetailsService() {
        assertThat(applicationContext.getBeanNamesForType(UserDetailsService.class))
                .isEmpty();
    }

    private String issueCsrfToken() {
        MvcTestResult result = mvc.get().uri("/actuator/health").exchange();
        Cookie cookie = result.getResponse().getCookie(CSRF_COOKIE);
        assertThat(cookie).isNotNull();
        return cookie.getValue();
    }

    private MockMvcTester.MockMvcRequestBuilder withCsrf(
            MockMvcTester.MockMvcRequestBuilder builder, String csrfToken) {
        return builder.cookie(new Cookie(CSRF_COOKIE, csrfToken)).header(CSRF_HEADER, csrfToken);
    }

    private MvcTestResult login(long memberId, String role) {
        return mvc.post()
                .uri("/security-test/login")
                .param("memberId", String.valueOf(memberId))
                .param("role", role)
                .exchange();
    }

    private Cookie loginAndGetSession(long memberId, String role) {
        Cookie session = login(memberId, role).getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        return session;
    }

    private Cookie createSession() {
        Cookie session = mvc.post()
                .uri("/security-test/session")
                .exchange()
                .getResponse()
                .getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        return session;
    }

    // Spring Session은 쿠키에 세션 ID를 Base64로 인코딩해 담는다.
    private static String decodeSessionId(Cookie sessionCookie) {
        return new String(Base64.getDecoder().decode(sessionCookie.getValue()), StandardCharsets.UTF_8);
    }
}
