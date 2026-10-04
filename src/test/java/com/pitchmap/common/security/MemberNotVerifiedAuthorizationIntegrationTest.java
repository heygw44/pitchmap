package com.pitchmap.common.security;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.common.trace.TraceIdFilter;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * 이메일 인증을 마치지 않은 회원의 접근 권한을 엔드포인트별로 확인한다.
 *
 * <p>이메일 인증이 필요한 경로 중 컨트롤러가 아직 없는 곳이 있다. 그래도 인가는 컨트롤러보다 앞선 필터에서 일어나므로,
 * 인가를 통과한 요청은 404 같은 컨트롤러 쪽 상태를 받고 401·403은 받지 않는다. 그래서 통과 여부를 "401·403이 아니다"로 판단한다.
 */
@IntegrationTest
@AutoConfigureMockMvc
class MemberNotVerifiedAuthorizationIntegrationTest {

    private static final String SESSION_COOKIE = "SESSION";
    private static final String VALID_PASSWORD = "Valid-pass1";
    private static final long MEMBER_ID = 7L;
    private static final String USER_ROLE = "USER";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // 02-1 3절과 07 3절 표에서 권한이 인증 회원, 본인확인, 캠프 리더인 행이다.
    static Stream<Arguments> verifiedOnlyEndpoints() {
        return Stream.of(
                Arguments.of(HttpMethod.PATCH, "/api/me"),
                Arguments.of(HttpMethod.POST, "/api/me/identity-verification"),
                Arguments.of(HttpMethod.POST, "/api/bakjis"),
                Arguments.of(HttpMethod.PATCH, "/api/bakjis/1"),
                Arguments.of(HttpMethod.DELETE, "/api/bakjis/1"),
                Arguments.of(HttpMethod.POST, "/api/bakjis/1/confirmations"),
                Arguments.of(HttpMethod.POST, "/api/bakjis/1/reports"),
                Arguments.of(HttpMethod.POST, "/api/spots/1/reviews"),
                Arguments.of(HttpMethod.PATCH, "/api/reviews/1"),
                Arguments.of(HttpMethod.DELETE, "/api/reviews/1"),
                Arguments.of(HttpMethod.POST, "/api/basecamps"),
                Arguments.of(HttpMethod.PATCH, "/api/basecamps/1"),
                Arguments.of(HttpMethod.POST, "/api/basecamps/1/close"),
                Arguments.of(HttpMethod.POST, "/api/basecamps/1/reopen"),
                Arguments.of(HttpMethod.POST, "/api/basecamps/1/confirm"),
                Arguments.of(HttpMethod.POST, "/api/basecamps/1/cancel"),
                Arguments.of(HttpMethod.PUT, "/api/basecamps/1/contact"),
                Arguments.of(HttpMethod.POST, "/api/basecamps/1/applications"),
                Arguments.of(HttpMethod.DELETE, "/api/basecamps/1/applications/me"),
                Arguments.of(HttpMethod.GET, "/api/basecamps/1/applications"),
                Arguments.of(HttpMethod.POST, "/api/basecamps/1/applications/2/approve"),
                Arguments.of(HttpMethod.POST, "/api/basecamps/1/applications/2/reject"),
                Arguments.of(HttpMethod.DELETE, "/api/basecamps/1/members/me"),
                Arguments.of(HttpMethod.POST, "/api/basecamps/1/members/2/kick"),
                Arguments.of(HttpMethod.GET, "/api/me/companion-reviews/pending"),
                Arguments.of(HttpMethod.POST, "/api/basecamps/1/companion-reviews"),
                Arguments.of(HttpMethod.GET, "/api/members/1/companion-reviews"),
                Arguments.of(HttpMethod.POST, "/api/member-reports"),
                Arguments.of(HttpMethod.POST, "/api/programs/1/applications"),
                Arguments.of(HttpMethod.POST, "/api/program-applications/1/pay"),
                Arguments.of(HttpMethod.POST, "/api/program-applications/1/cancel"),
                Arguments.of(HttpMethod.POST, "/api/programs/1/vacancy-alerts"),
                Arguments.of(HttpMethod.DELETE, "/api/programs/1/vacancy-alerts"));
    }

    // 권한이 로그인인 행(이메일 인증 API 제외)이다. 인증을 마치지 않은 회원도 쓸 수 있어야 한다.
    static Stream<Arguments> loginLevelEndpoints() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/me"),
                Arguments.of(HttpMethod.DELETE, "/api/me"),
                Arguments.of(HttpMethod.GET, "/api/me/trust"),
                Arguments.of(HttpMethod.GET, "/api/me/basecamps"),
                Arguments.of(HttpMethod.GET, "/api/me/companion-reviews/received"),
                Arguments.of(HttpMethod.GET, "/api/me/program-applications"),
                Arguments.of(HttpMethod.GET, "/api/me/notifications"),
                Arguments.of(HttpMethod.GET, "/api/me/notifications/unread-count"),
                Arguments.of(HttpMethod.POST, "/api/me/notifications/1/read"),
                Arguments.of(HttpMethod.POST, "/api/me/notifications/read-all"),
                Arguments.of(HttpMethod.GET, "/api/me/notification-settings"),
                Arguments.of(HttpMethod.PUT, "/api/me/notification-settings"),
                Arguments.of(HttpMethod.POST, "/api/auth/logout"));
    }

    // 공개 읽기 행 가운데 이메일 인증이 필요한 경로와 앞부분이 같은 것들이다.
    static Stream<Arguments> publicReadEndpoints() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/basecamps"),
                Arguments.of(HttpMethod.GET, "/api/basecamps/1"),
                Arguments.of(HttpMethod.GET, "/api/spots/1/reviews"),
                Arguments.of(HttpMethod.GET, "/api/members/1/profile"));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("verifiedOnlyEndpoints")
    @DisplayName("[F-01][TR-03] 이메일 인증을 마치지 않은 회원이 인증 회원 이상의 경로를 부르면 403 MEMBER_NOT_VERIFIED로 응답한다")
    void unverifiedMemberIsRejectedOnVerifiedOnlyEndpoints(HttpMethod method, String path) {
        Cookie session = loginAsProbe(MEMBER_ID, USER_ROLE, false);

        MvcTestResult result = call(method, path, session);

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        assertThat(result).bodyJson().extractingPath("$.message").isEqualTo("이메일 인증이 필요합니다.");
        String headerTraceId = result.getResponse().getHeader(TraceIdFilter.HEADER);
        assertThat(headerTraceId).isNotBlank();
        assertThat(result).bodyJson().extractingPath("$.traceId").isEqualTo(headerTraceId);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("verifiedOnlyEndpoints")
    @DisplayName("[F-01][TR-03] 이메일 인증을 마친 회원은 인증 회원 이상의 경로에서 인증·권한 오류 없이 컨트롤러까지 간다")
    void verifiedMemberPassesVerifiedOnlyEndpoints(HttpMethod method, String path) {
        Cookie session = loginAsProbe(MEMBER_ID, USER_ROLE, true);

        MvcTestResult result = call(method, path, session);

        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("verifiedOnlyEndpoints")
    @DisplayName("[F-01][TR-03] 로그인하지 않고 인증 회원 이상의 경로를 부르면 403이 아니라 401 AUTHENTICATION_REQUIRED로 응답한다")
    void anonymousIsUnauthorizedOnVerifiedOnlyEndpoints(HttpMethod method, String path) {
        MvcTestResult result = call(method, path, null);

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("loginLevelEndpoints")
    @DisplayName("[F-01][TR-03] 이메일 인증을 마치지 않은 회원도 로그인만 필요한 경로는 인증·권한 오류 없이 쓸 수 있다")
    void unverifiedMemberPassesLoginLevelEndpoints(HttpMethod method, String path) {
        Cookie session = loginAsProbe(MEMBER_ID, USER_ROLE, false);

        MvcTestResult result = call(method, path, session);

        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("publicReadEndpoints")
    @DisplayName("[F-01][TR-03] 이메일 인증을 마치지 않은 회원도 공개 읽기 경로는 쓸 수 있다")
    void unverifiedMemberPassesPublicReadEndpoints(HttpMethod method, String path) {
        Cookie session = loginAsProbe(MEMBER_ID, USER_ROLE, false);

        MvcTestResult result = call(method, path, session);

        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    @Test
    @DisplayName("[F-01][TR-03] 이메일 인증을 마치지 않은 회원도 인증 코드 확인과 재발송 API에는 닿는다")
    void unverifiedMemberReachesEmailVerificationApis() {
        Cookie session = loginAsRealMember();

        MvcTestResult verify = mvc.post()
                .uri("/api/me/email-verification")
                .with(csrf())
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"000000\"}")
                .exchange();
        MvcTestResult resend = mvc.post()
                .uri("/api/me/email-verification/resend")
                .with(csrf())
                .cookie(session)
                .exchange();

        // 발급한 코드가 없으므로 코드 확인은 코드 오류까지 가야 한다. 인가에서 막혔다면 403이었을 것이다.
        assertThat(verify).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(verify).bodyJson().extractingPath("$.code").isEqualTo("EMAIL_CODE_INVALID");
        assertThat(resend).hasStatus(HttpStatus.NO_CONTENT);
    }

    @ParameterizedTest(name = "verified={0}")
    @MethodSource("verifiedFlags")
    @DisplayName("[F-01][ADR-002] CSRF 토큰이 없는 쓰기 요청은 인증 여부와 상관없이 403 ACCESS_DENIED로 응답한다")
    void missingCsrfTokenIsAccessDeniedRegardlessOfVerification(boolean emailVerified) {
        Cookie session = loginAsProbe(MEMBER_ID, USER_ROLE, emailVerified);

        MvcTestResult result = mvc.post()
                .uri("/api/bakjis")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
    }

    static Stream<Arguments> verifiedFlags() {
        return Stream.of(Arguments.of(false), Arguments.of(true));
    }

    @ParameterizedTest(name = "verified={0}")
    @MethodSource("verifiedFlags")
    @DisplayName("[F-01][ADR-002] 일반 회원이 관리자 경로를 부르면 인증 여부와 상관없이 403 ACCESS_DENIED로 응답한다")
    void adminPathIsAccessDeniedForUserRegardlessOfVerification(boolean emailVerified) {
        Cookie session = loginAsProbe(MEMBER_ID, USER_ROLE, emailVerified);

        MvcTestResult result = call(HttpMethod.GET, "/api/admin/audit-logs", session);

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
    }

    @Test
    @DisplayName("[F-01][ADR-002] 관리자 경로는 관리자 역할만 보고 이메일 인증 권한은 요구하지 않는다")
    void adminRoleDoesNotNeedEmailVerifiedAuthority() {
        Cookie session = loginAsProbe(1L, "ADMIN", false);

        MvcTestResult result = call(HttpMethod.GET, "/api/admin/audit-logs", session);

        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    private MvcTestResult call(HttpMethod method, String path, Cookie session) {
        MockMvcTester.MockMvcRequestBuilder request =
                mvc.method(method).uri(path).with(csrf()).contentType(MediaType.APPLICATION_JSON);
        if (session != null) {
            request.cookie(session);
        }
        return request.content("{}").exchange();
    }

    private Cookie loginAsProbe(long memberId, String role, boolean emailVerified) {
        MvcTestResult result = mvc.post()
                .uri("/security-test/login")
                .param("memberId", String.valueOf(memberId))
                .param("role", role)
                .param("emailVerified", String.valueOf(emailVerified))
                .exchange();
        Cookie session = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        return session;
    }

    // 인증 코드 API는 DB의 회원을 읽으므로, 이 로그인만 프로브 대신 실제 회원과 로그인 API를 쓴다.
    private Cookie loginAsRealMember() {
        Member member = memberRepository.save(aMember()
                .email(TestSequence.email())
                .passwordHash(passwordEncoder.encode(VALID_PASSWORD))
                .build());
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
}
