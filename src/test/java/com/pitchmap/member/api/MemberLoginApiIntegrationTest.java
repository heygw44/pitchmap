package com.pitchmap.member.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.domain.LoginLockout;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@AutoConfigureMockMvc
class MemberLoginApiIntegrationTest {

    private static final String PASSWORD = "Valid-pass1";
    private static final String WRONG_PASSWORD = "Wrong-pass-1!";
    private static final String SESSION_COOKIE = "SESSION";
    private static final String LOCAL_IP = "127.0.0.1";
    // DATETIME 컬럼에 UTC 시각을 문자열로 넣는다. 드라이버의 시간대 변환이 끼어들지 않게 하려는 것이다.
    private static final DateTimeFormatter DATETIME_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC);

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

    @Test
    @DisplayName("[F-02] 올바른 이메일과 비밀번호로 로그인하면 200과 SESSION 쿠키를 응답하고 성공을 기록한다")
    void loginSucceedsWithSessionCookie() {
        String email = TestSequence.email();
        Member member = saveMember(email);

        MvcTestResult result = login(email, PASSWORD);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.memberId")
                .isEqualTo(member.getId().intValue());
        assertThat(result).bodyJson().extractingPath("$.nickname").isEqualTo(member.getNickname());
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("UNVERIFIED");
        assertThat(result).bodyJson().extractingPath("$.role").isEqualTo("USER");
        assertThat(result.getResponse().getCookie(SESSION_COOKIE)).isNotNull();
        assertThat(count("login_history WHERE member_id = ? AND success = TRUE AND ip = ?", member.getId(), LOCAL_IP))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("[F-02] 로그인 성공 응답에는 이메일·비밀번호·해시가 없다")
    void successResponseHasNoSensitiveFields() {
        String email = TestSequence.email();
        saveMember(email);

        MvcTestResult result = login(email, PASSWORD);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().doesNotHavePath("$.email");
        assertThat(result).bodyJson().doesNotHavePath("$.password");
        assertThat(result).bodyJson().doesNotHavePath("$.passwordHash");
    }

    @Test
    @DisplayName("[F-02] 대소문자가 다른 이메일로도 로그인되고 기록에는 소문자 이메일이 남는다")
    void emailIsNormalizedToLowerCase() {
        String email = TestSequence.email();
        saveMember(email);

        MvcTestResult result = login(email.toUpperCase(Locale.ROOT), PASSWORD);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(count("login_history WHERE attempted_email = ? AND success = TRUE", email))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("[F-02] 이미 세션 쿠키가 있는 상태에서 로그인하면 세션 ID가 바뀌고 이전 세션은 저장소에서 사라진다")
    void loginReplacesPreviousSession() {
        String email = TestSequence.email();
        saveMember(email);
        Cookie before = login(email, PASSWORD).getResponse().getCookie(SESSION_COOKIE);
        assertThat(before).isNotNull();
        String oldSessionId = jdbc.queryForObject("SELECT SESSION_ID FROM SPRING_SESSION", String.class);

        MvcTestResult result = loginWithSession(email, PASSWORD, before);

        Cookie after = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(after).isNotNull();
        assertThat(after.getValue()).isNotEqualTo(before.getValue());
        List<String> sessionIds = jdbc.queryForList("SELECT SESSION_ID FROM SPRING_SESSION", String.class);
        assertThat(sessionIds).hasSize(1).doesNotContain(oldSessionId);
    }

    @Test
    @DisplayName("[F-02] 비밀번호가 틀린 경우와 없는 이메일은 같은 401 LOGIN_FAILED를 응답하고 실패를 기록한다")
    void wrongPasswordAndUnknownEmailGiveSameResponse() {
        String email = TestSequence.email();
        Member member = saveMember(email);
        String unknownEmail = TestSequence.email();

        MvcTestResult wrongPassword = login(email, WRONG_PASSWORD);
        MvcTestResult unknown = login(unknownEmail, PASSWORD);

        assertThat(wrongPassword).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(unknown).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword).bodyJson().extractingPath("$.code").isEqualTo("LOGIN_FAILED");
        assertThat(unknown).bodyJson().extractingPath("$.code").isEqualTo("LOGIN_FAILED");
        assertThat(bodyMessage(unknown)).isEqualTo(bodyMessage(wrongPassword));
        assertThat(wrongPassword.getResponse().getCookie(SESSION_COOKIE)).isNull();
        assertThat(count("login_history WHERE member_id = ? AND success = FALSE", member.getId()))
                .isEqualTo(1);
        assertThat(count("login_history WHERE member_id IS NULL AND attempted_email = ?", unknownEmail))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("[F-02][PW-03] 5번 실패하면 올바른 비밀번호로도 429 LOGIN_LOCKED와 Retry-After를 응답하고 잠긴 동안은 기록하지 않는다")
    void fiveFailuresLockLogin() {
        String email = TestSequence.email();
        saveMember(email);
        failLogin(email, 5);

        MvcTestResult locked = login(email, PASSWORD);

        assertThat(locked).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(locked).bodyJson().extractingPath("$.code").isEqualTo("LOGIN_LOCKED");
        assertThat(locked.getResponse().getHeader(HttpHeaders.RETRY_AFTER))
                .isEqualTo(String.valueOf(LoginLockout.WINDOW.toSeconds()));
        assertThat(locked.getResponse().getCookie(SESSION_COOKIE)).isNull();
        assertThat(count("login_history WHERE attempted_email = ?", email)).isEqualTo(5);
    }

    @Test
    @DisplayName("[F-02][PW-03] 4번 실패한 뒤에는 올바른 비밀번호로 로그인할 수 있다")
    void fourFailuresDoNotLockLogin() {
        String email = TestSequence.email();
        saveMember(email);
        failLogin(email, 4);

        MvcTestResult result = login(email, PASSWORD);

        assertThat(result).hasStatus(HttpStatus.OK);
    }

    @Test
    @DisplayName("[F-02][PW-03] 잠금이 풀리기 1초 전까지는 429이고, 풀리는 시각이 되면 다시 로그인할 수 있다")
    void lockIsReleasedAfterWindow() {
        String email = TestSequence.email();
        saveMember(email);
        failLogin(email, 5);

        clock.advance(LoginLockout.WINDOW.minusSeconds(1));
        MvcTestResult stillLocked = login(email, PASSWORD);
        clock.advance(Duration.ofSeconds(1));
        MvcTestResult released = login(email, PASSWORD);

        assertThat(stillLocked).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(stillLocked.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
        assertThat(released).hasStatus(HttpStatus.OK);
    }

    @Test
    @DisplayName("[F-02][PW-03] 마지막 성공 이후의 실패만 센다")
    void onlyFailuresAfterLastSuccessCount() {
        String email = TestSequence.email();
        saveMember(email);
        failLogin(email, 4);
        assertThat(login(email, PASSWORD)).hasStatus(HttpStatus.OK);
        failLogin(email, 4);

        MvcTestResult notLocked = login(email, PASSWORD);

        assertThat(notLocked).hasStatus(HttpStatus.OK);
        failLogin(email, 5);
        assertThat(login(email, PASSWORD)).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("[F-02][PW-03] 한 이메일이 잠겨도 다른 이메일은 로그인할 수 있다")
    void lockDoesNotAffectOtherEmail() {
        String lockedEmail = TestSequence.email();
        String otherEmail = TestSequence.email();
        saveMember(lockedEmail);
        saveMember(otherEmail);
        failLogin(lockedEmail, 5);

        MvcTestResult result = login(otherEmail, PASSWORD);

        assertThat(result).hasStatus(HttpStatus.OK);
    }

    @Test
    @DisplayName("[F-02][TR-03] 이메일을 인증하지 않은 회원과 인증한 회원 모두 로그인할 수 있다")
    void unverifiedAndActiveMembersCanLogIn() {
        String unverifiedEmail = TestSequence.email();
        String activeEmail = TestSequence.email();
        saveMember(unverifiedEmail);
        Member active = saveMember(activeEmail);
        jdbc.update("UPDATE member SET status = 'ACTIVE', role = 'ADMIN' WHERE id = ?", active.getId());

        MvcTestResult unverified = login(unverifiedEmail, PASSWORD);
        MvcTestResult activeResult = login(activeEmail, PASSWORD);

        assertThat(unverified).hasStatus(HttpStatus.OK);
        assertThat(unverified).bodyJson().extractingPath("$.status").isEqualTo("UNVERIFIED");
        assertThat(activeResult).hasStatus(HttpStatus.OK);
        assertThat(activeResult).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
        assertThat(activeResult).bodyJson().extractingPath("$.role").isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("[F-02][SN-12] 정지 기간이 있는 회원은 403 MEMBER_SUSPENDED와 suspendedUntil을 응답하고 실패로 기록하지 않는다")
    void temporarilySuspendedMemberIsRejectedWithSuspendedUntil() {
        String email = TestSequence.email();
        Member member = saveMember(email);
        Instant until = MutableClock.DEFAULT_INSTANT.plus(Duration.ofDays(7));
        jdbc.update(
                "UPDATE member SET status = 'SUSPENDED', suspended_until = ? WHERE id = ?",
                DATETIME_UTC.format(until),
                member.getId());

        MvcTestResult result = login(email, PASSWORD);

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_SUSPENDED");
        assertThat(result).bodyJson().extractingPath("$.suspendedUntil").isEqualTo(until.toString());
        assertThat(result.getResponse().getCookie(SESSION_COOKIE)).isNull();
        assertThat(count("login_history WHERE attempted_email = ?", email)).isZero();
    }

    @Test
    @DisplayName("[F-02][SN-12] 영구 정지 회원의 403 응답에는 suspendedUntil 키가 없다")
    void permanentlySuspendedMemberResponseHasNoSuspendedUntil() {
        String email = TestSequence.email();
        Member member = saveMember(email);
        jdbc.update("UPDATE member SET status = 'SUSPENDED', suspended_until = NULL WHERE id = ?", member.getId());

        MvcTestResult result = login(email, PASSWORD);

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_SUSPENDED");
        assertThat(result).bodyJson().doesNotHavePath("$.suspendedUntil");
    }

    @Test
    @DisplayName("[F-02][SN-12] 정지 회원이라도 비밀번호가 틀리면 정지 여부를 알리지 않고 LOGIN_FAILED를 응답한다")
    void suspendedMemberWithWrongPasswordGetsLoginFailed() {
        String email = TestSequence.email();
        Member member = saveMember(email);
        jdbc.update("UPDATE member SET status = 'SUSPENDED', suspended_until = NULL WHERE id = ?", member.getId());

        MvcTestResult result = login(email, WRONG_PASSWORD);

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("LOGIN_FAILED");
    }

    @Test
    @DisplayName("[F-02] 탈퇴한 회원의 옛 이메일로는 로그인할 수 없다")
    void withdrawnMemberCannotLogIn() {
        String email = TestSequence.email();
        Member member = saveMember(email);
        jdbc.update("UPDATE member SET status = 'WITHDRAWN', email = NULL WHERE id = ?", member.getId());

        MvcTestResult result = login(email, PASSWORD);

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("LOGIN_FAILED");
    }

    @Test
    @DisplayName("[F-02] 로그아웃하면 204를 응답하고 같은 세션 쿠키로 다시 로그아웃하면 401을 응답한다")
    void logoutInvalidatesSession() {
        String email = TestSequence.email();
        saveMember(email);
        Cookie session = login(email, PASSWORD).getResponse().getCookie(SESSION_COOKIE);

        MvcTestResult logout = logout(session);
        MvcTestResult logoutAgain = logout(session);

        assertThat(logout).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(logoutAgain).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(logoutAgain).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(count("SPRING_SESSION")).isZero();
    }

    @Test
    @DisplayName("[F-02] 로그인하지 않고 로그인이 필요한 경로를 부르면 401 AUTHENTICATION_REQUIRED를 응답한다")
    void protectedPathWithoutLoginReturnsUnauthorized() {
        MvcTestResult result = mvc.get().uri("/api/me").exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(result).bodyJson().extractingPath("$.traceId").asString().isNotBlank();
    }

    @Test
    @DisplayName("[F-02] CSRF 토큰 없이 로그인 요청을 보내면 403 ACCESS_DENIED를 응답하고 시도를 기록하지 않는다")
    void loginWithoutCsrfTokenIsForbidden() {
        String email = TestSequence.email();
        saveMember(email);

        MvcTestResult result = mvc.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, PASSWORD))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        assertThat(count("login_history")).isZero();
    }

    @Test
    @DisplayName("[F-02] 비밀번호가 비어 있으면 400 INVALID_INPUT을 응답하고 시도를 기록하지 않는다")
    void blankPasswordReturnsBadRequest() {
        MvcTestResult result = login(TestSequence.email(), " ");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(count("login_history")).isZero();
    }

    private Member saveMember(String email) {
        return memberRepository.save(aMember()
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .build());
    }

    private void failLogin(String email, int times) {
        for (int i = 0; i < times; i++) {
            assertThat(login(email, WRONG_PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);
        }
    }

    private MvcTestResult login(String email, String password) {
        return loginRequest(email, password).exchange();
    }

    private MvcTestResult loginWithSession(String email, String password, Cookie session) {
        return loginRequest(email, password).cookie(session).exchange();
    }

    private MockMvcTester.MockMvcRequestBuilder loginRequest(String email, String password) {
        return mvc.post()
                .uri("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, password));
    }

    private MvcTestResult logout(Cookie session) {
        return mvc.post().uri("/api/auth/logout").with(csrf()).cookie(session).exchange();
    }

    private static String loginBody(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private static String bodyMessage(MvcTestResult result) {
        String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.message");
    }

    private int count(String fromAndWhere, Object... args) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + fromAndWhere, Integer.class, args);
        return count == null ? 0 : count;
    }
}
