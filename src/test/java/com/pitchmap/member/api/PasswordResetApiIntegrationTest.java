package com.pitchmap.member.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.pitchmap.common.mail.MailMessage;
import com.pitchmap.common.mail.TestMailSender;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.application.PasswordResetMails;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.domain.PasswordResetPolicy;
import com.pitchmap.member.domain.PasswordResetToken;
import com.pitchmap.member.domain.PasswordResetTokenRepository;
import com.pitchmap.member.domain.ResetToken;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.notification.application.OutboxPublisher;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
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
class PasswordResetApiIntegrationTest {

    private static final String OLD_PASSWORD = "Valid-pass1";
    private static final String NEW_PASSWORD = "Newer-pass2";
    private static final String SESSION_COOKIE = "SESSION";
    private static final String REQUEST_PATH = "/api/auth/password-reset/request";
    private static final String CONFIRM_PATH = "/api/auth/password-reset/confirm";
    private static final String LOGIN_REQUIRED_PATH = "/api/me";
    private static final String EVENT_TYPE = "PASSWORD_RESET_REQUESTED";
    private static final String INVALID_TOKEN = "PASSWORD_RESET_TOKEN_INVALID";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OutboxPublisher publisher;

    @Autowired
    private TestMailSender mailSender;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @BeforeEach
    void resetMailSender() {
        mailSender.reset();
    }

    @Test
    @DisplayName("[PW-04] 가입된 이메일과 없는 이메일 모두 본문 없는 204로 같게 응답하고, 가입된 경우에만 이벤트가 쌓인다")
    void requestResponseDoesNotRevealMembership() {
        Member member = saveMember();

        MvcTestResult registered = postJson(REQUEST_PATH, emailBody(member.getEmail()), null);
        MvcTestResult unknown = postJson(REQUEST_PATH, emailBody(TestSequence.email()), null);

        assertThat(registered).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(unknown).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(registered.getResponse().getContentAsByteArray()).isEmpty();
        assertThat(unknown.getResponse().getContentAsByteArray()).isEmpty();
        assertThat(unknown.getResponse().getContentType())
                .isEqualTo(registered.getResponse().getContentType());
        assertThat(unknown.getResponse().getHeaderNames())
                .containsExactlyInAnyOrderElementsOf(registered.getResponse().getHeaderNames());
        assertThat(unknown.getResponse().getCookies())
                .hasSameSizeAs(registered.getResponse().getCookies());
        assertThat(count("outbox_event WHERE event_type = ? AND status = 'PENDING'", EVENT_TYPE))
                .isEqualTo(1);
        assertThat(count("outbox_event WHERE event_type = ? AND aggregate_id = ?", EVENT_TYPE, member.getId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("[F-02][PW-02] 요청하고 메일의 토큰으로 확인하면 204이고, 예전 비밀번호는 401 LOGIN_FAILED, 새 비밀번호는 200이다")
    void resetsPasswordEndToEnd() {
        Member member = saveMember();

        assertThat(postJson(REQUEST_PATH, emailBody(member.getEmail()), null)).hasStatus(HttpStatus.NO_CONTENT);
        publisher.publishPending();
        String token = tokenFromMail();
        MvcTestResult confirm = postJson(CONFIRM_PATH, confirmBody(token, NEW_PASSWORD), null);

        assertThat(confirm).hasStatus(HttpStatus.NO_CONTENT);
        MvcTestResult oldLogin = loginRequest(member.getEmail(), OLD_PASSWORD);
        assertThat(oldLogin).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(oldLogin).bodyJson().extractingPath("$.code").isEqualTo("LOGIN_FAILED");
        assertThat(loginRequest(member.getEmail(), NEW_PASSWORD)).hasStatus(HttpStatus.OK);
    }

    @Test
    @DisplayName("[PW-02] 확인하면 그 회원의 세션이 모두 사라지고 쿠키는 401이며 다른 회원의 세션은 그대로다")
    void invalidatesAllSessionsOfTheMemberOnly() {
        Member member = saveMember();
        Member other = saveMember();
        Cookie first = login(member);
        Cookie second = login(member);
        Cookie otherSession = login(other);
        assertThat(sessionCount(member)).isEqualTo(2);

        MvcTestResult confirm = postJson(CONFIRM_PATH, confirmBody(issueToken(member), NEW_PASSWORD), null);

        assertThat(confirm).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(sessionCount(member)).isZero();
        assertThat(getWithSession(first)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(getWithSession(second)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(sessionCount(other)).isEqualTo(1);
        assertThat(getWithSession(otherSession).getResponse().getStatus()).isNotEqualTo(401);
    }

    @Test
    @DisplayName("[PW-02] 확인 요청이 그 회원의 세션 쿠키를 달고 와도 응답 뒤에 그 세션은 되살아나지 않는다")
    void sessionOfTheConfirmRequestIsNotResurrected() {
        Member member = saveMember();
        Cookie session = login(member);

        MvcTestResult confirm = postJson(CONFIRM_PATH, confirmBody(issueToken(member), NEW_PASSWORD), session);

        assertThat(confirm).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(sessionCount(member)).isZero();
        assertThat(getWithSession(session)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("[PW-02] 같은 토큰을 두 번 쓰면 두 번째는 400 PASSWORD_RESET_TOKEN_INVALID이고 비밀번호는 첫 번째 값이다")
    void tokenIsSingleUse() {
        Member member = saveMember();
        String token = issueToken(member);

        MvcTestResult first = postJson(CONFIRM_PATH, confirmBody(token, NEW_PASSWORD), null);
        MvcTestResult second = postJson(CONFIRM_PATH, confirmBody(token, "Third-pass3"), null);

        assertThat(first).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(second).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(second).bodyJson().extractingPath("$.code").isEqualTo(INVALID_TOKEN);
        assertThat(loginRequest(member.getEmail(), NEW_PASSWORD)).hasStatus(HttpStatus.OK);
        assertThat(loginRequest(member.getEmail(), "Third-pass3")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("[PW-02] 만료 1초 전까지는 되고, 30분이 되는 순간과 그 뒤에는 400 PASSWORD_RESET_TOKEN_INVALID이다")
    void expiryBoundary() {
        String justBefore = issueToken(saveMember());
        String atBoundary = issueToken(saveMember());
        String after = issueToken(saveMember());

        clock.advance(PasswordResetPolicy.TOKEN_VALIDITY.minusSeconds(1));
        MvcTestResult beforeExpiry = postJson(CONFIRM_PATH, confirmBody(justBefore, NEW_PASSWORD), null);
        clock.advance(Duration.ofSeconds(1));
        MvcTestResult onExpiry = postJson(CONFIRM_PATH, confirmBody(atBoundary, NEW_PASSWORD), null);
        clock.advance(Duration.ofHours(1));
        MvcTestResult afterExpiry = postJson(CONFIRM_PATH, confirmBody(after, NEW_PASSWORD), null);

        assertThat(beforeExpiry).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(onExpiry).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(onExpiry).bodyJson().extractingPath("$.code").isEqualTo(INVALID_TOKEN);
        assertThat(afterExpiry).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(afterExpiry).bodyJson().extractingPath("$.code").isEqualTo(INVALID_TOKEN);
    }

    @Test
    @DisplayName("[PW-02] 발급한 적 없는 토큰과 형식이 틀린 토큰은 모두 400 PASSWORD_RESET_TOKEN_INVALID이고 본문 모양이 같다")
    void unknownAndMalformedTokensGiveSameResponse() {
        String neverIssued = ResetToken.generate(new SecureRandom()).value();
        List<String> inputs = List.of(neverIssued, "abc", "a".repeat(42), "a".repeat(44), "a".repeat(42) + "!");

        List<MvcTestResult> results = inputs.stream()
                .map(input -> postJson(CONFIRM_PATH, confirmBody(input, NEW_PASSWORD), null))
                .toList();

        for (MvcTestResult result : results) {
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(INVALID_TOKEN);
            assertThat(bodyKeys(result)).isEqualTo(bodyKeys(results.get(0)));
            assertThat(bodyMessage(result)).isEqualTo(bodyMessage(results.get(0)));
        }
    }

    @Test
    @DisplayName("[PW-01] 규칙을 어긴 새 비밀번호는 400 MEMBER_PASSWORD_POLICY이고 토큰은 소모되지 않아 다시 쓸 수 있다")
    void policyViolationDoesNotConsumeToken() {
        Member member = saveMember();
        String token = issueToken(member);

        for (String weak : List.of("Abcde-1ab", "Abcdefghij1", "Valid pass1!")) {
            MvcTestResult result = postJson(CONFIRM_PATH, confirmBody(token, weak), null);

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_PASSWORD_POLICY");
            assertThat(count("password_reset_token WHERE member_id = ? AND used_at IS NULL", member.getId()))
                    .isEqualTo(1);
        }
        MvcTestResult retry = postJson(CONFIRM_PATH, confirmBody(token, NEW_PASSWORD), null);

        assertThat(retry).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(loginRequest(member.getEmail(), NEW_PASSWORD)).hasStatus(HttpStatus.OK);
    }

    @Test
    @DisplayName("[PW-02] 재설정에 성공하면 그 회원의 사용하지 않은 다른 토큰도 무효가 된다")
    void otherUnusedTokensAreInvalidated() {
        Member member = saveMember();
        String first = issueToken(member);
        String second = issueToken(member);

        MvcTestResult used = postJson(CONFIRM_PATH, confirmBody(first, NEW_PASSWORD), null);
        MvcTestResult reused = postJson(CONFIRM_PATH, confirmBody(second, "Third-pass3"), null);

        assertThat(used).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(reused).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(reused).bodyJson().extractingPath("$.code").isEqualTo(INVALID_TOKEN);
        assertThat(count("password_reset_token WHERE member_id = ? AND used_at IS NOT NULL", member.getId()))
                .isEqualTo(2);
        assertThat(loginRequest(member.getEmail(), "Third-pass3")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("[PW-02] 확인이 실패하면(만료된 토큰, 규칙을 어긴 비밀번호) 그 회원의 세션은 지워지지 않는다")
    void failedConfirmKeepsSessions() {
        Member expiredMember = saveMember();
        Member weakMember = saveMember();
        Cookie expiredSession = login(expiredMember);
        Cookie weakSession = login(weakMember);
        String expiredToken = issueToken(expiredMember);
        String weakToken = issueToken(weakMember);

        MvcTestResult weak = postJson(CONFIRM_PATH, confirmBody(weakToken, "Weak-1"), null);
        clock.advance(PasswordResetPolicy.TOKEN_VALIDITY);
        MvcTestResult expired = postJson(CONFIRM_PATH, confirmBody(expiredToken, NEW_PASSWORD), null);

        assertThat(expired).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(expired).bodyJson().extractingPath("$.code").isEqualTo(INVALID_TOKEN);
        assertThat(weak).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(weak).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_PASSWORD_POLICY");
        assertThat(sessionCount(expiredMember)).isEqualTo(1);
        assertThat(sessionCount(weakMember)).isEqualTo(1);
        assertThat(getWithSession(expiredSession).getResponse().getStatus()).isNotEqualTo(401);
        assertThat(getWithSession(weakSession).getResponse().getStatus()).isNotEqualTo(401);
    }

    @Test
    @DisplayName("[PW-02][PW-03] 재설정해도 회원 상태는 그대로이고 로그인 실패 기록은 지워지지 않는다")
    void keepsStatusAndLoginFailureHistory() {
        Member unverified = saveMember();
        Member active = saveMember();
        jdbc.update("UPDATE member SET status = 'ACTIVE' WHERE id = ?", active.getId());
        loginRequest(unverified.getEmail(), "Wrong-pass-1!");
        loginRequest(unverified.getEmail(), "Wrong-pass-1!");

        postJson(CONFIRM_PATH, confirmBody(issueToken(unverified), NEW_PASSWORD), null);
        postJson(CONFIRM_PATH, confirmBody(issueToken(active), NEW_PASSWORD), null);

        assertThat(memberStatus(unverified)).isEqualTo("UNVERIFIED");
        assertThat(memberStatus(active)).isEqualTo("ACTIVE");
        assertThat(count("login_history WHERE member_id = ? AND success = FALSE", unverified.getId()))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("[PW-04] 이메일이 비었거나 형식이 틀리면 400 INVALID_INPUT이고, CSRF 토큰이 없으면 두 경로 모두 403 ACCESS_DENIED다")
    void validationAndCsrf() {
        for (String body : List.of(
                "{\"email\":\"\"}",
                "{\"email\":\"not-an-email\"}",
                "{\"email\":\"user@localhost\"}",
                "{\"email\":\"user@[127.0.0.1]\"}",
                "{}")) {
            MvcTestResult result = postJson(REQUEST_PATH, body, null);

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
        assertThat(count("outbox_event")).isZero();

        MvcTestResult requestWithoutCsrf = mvc.post()
                .uri(REQUEST_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(emailBody(TestSequence.email()))
                .exchange();
        MvcTestResult confirmWithoutCsrf = mvc.post()
                .uri(CONFIRM_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmBody(ResetToken.generate(new SecureRandom()).value(), NEW_PASSWORD))
                .exchange();

        assertThat(requestWithoutCsrf).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(requestWithoutCsrf).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        assertThat(confirmWithoutCsrf).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(confirmWithoutCsrf).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
    }

    @Test
    @DisplayName("[PW-02] 토큰은 해시로만 저장되고, 토큰과 새 비밀번호는 접속 기록·아웃박스·응답 본문에 남지 않는다")
    void secretsAreNotLeaked() {
        Member member = saveMember();
        postJson(REQUEST_PATH, emailBody(member.getEmail()), null);
        publisher.publishPending();
        String token = tokenFromMail();

        MvcTestResult weak = postJson(CONFIRM_PATH, confirmBody(token, "Weak-1"), null);
        MvcTestResult confirm = postJson(CONFIRM_PATH, confirmBody(token, NEW_PASSWORD), null);
        MvcTestResult reused = postJson(CONFIRM_PATH, confirmBody(token, NEW_PASSWORD), null);
        loginRequest(member.getEmail(), NEW_PASSWORD);

        assertThat(count("password_reset_token WHERE token_hash = ?", token)).isZero();
        assertThat(count("password_reset_token WHERE token_hash = ?", ResetToken.hash(token)))
                .isEqualTo(1);
        for (String secret : List.of(token, NEW_PASSWORD)) {
            String pattern = "%" + secret + "%";
            assertThat(count("login_history WHERE attempted_email LIKE ? OR ip LIKE ?", pattern, pattern))
                    .isZero();
            assertThat(count("outbox_event WHERE payload LIKE ? OR last_error LIKE ?", pattern, pattern))
                    .isZero();
            for (MvcTestResult result : List.of(weak, confirm, reused)) {
                assertThat(bodyOf(result)).doesNotContain(secret);
            }
        }
    }

    private Member saveMember() {
        return memberRepository.save(aMember()
                .email(TestSequence.email())
                .passwordHash(passwordEncoder.encode(OLD_PASSWORD))
                .now(clock.instant())
                .build());
    }

    // 토큰 원값은 DB에 없다. 메일을 거치지 않는 테스트는 토큰을 직접 만들어 해시만 저장한다.
    private String issueToken(Member member) {
        ResetToken token = ResetToken.generate(new SecureRandom());
        tokenRepository.save(PasswordResetToken.issue(member.getId(), ResetToken.hash(token.value()), clock.instant()));
        return token.value();
    }

    private String tokenFromMail() {
        List<MailMessage> sent = mailSender.sent();
        assertThat(sent).hasSize(1);
        return PasswordResetMails.extractToken(sent.get(0));
    }

    private Cookie login(Member member) {
        MvcTestResult result = loginRequest(member.getEmail(), OLD_PASSWORD);
        assertThat(result).hasStatus(HttpStatus.OK);
        Cookie session = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        return session;
    }

    private MvcTestResult loginRequest(String email, String password) {
        String body = "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
        return postJson("/api/auth/login", body, null);
    }

    private MvcTestResult getWithSession(Cookie session) {
        return mvc.get().uri(LOGIN_REQUIRED_PATH).cookie(session).exchange();
    }

    private MvcTestResult postJson(String path, String body, Cookie session) {
        MockMvcTester.MockMvcRequestBuilder request = mvc.post().uri(path).with(csrf());
        if (session != null) {
            request.cookie(session);
        }
        return request.contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private static String emailBody(String email) {
        return "{\"email\":\"%s\"}".formatted(email);
    }

    private static String confirmBody(String token, String newPassword) {
        return "{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(token, newPassword);
    }

    private static String bodyOf(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private static String bodyMessage(MvcTestResult result) {
        return JsonPath.read(bodyOf(result), "$.message");
    }

    private static Object bodyKeys(MvcTestResult result) {
        Map<String, Object> body = JsonPath.read(bodyOf(result), "$");
        return body.keySet();
    }

    private int sessionCount(Member member) {
        return count("SPRING_SESSION WHERE PRINCIPAL_NAME = ?", String.valueOf(member.getId()));
    }

    private String memberStatus(Member member) {
        return jdbc.queryForObject("SELECT status FROM member WHERE id = ?", String.class, member.getId());
    }

    private int count(String fromAndWhere, Object... args) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + fromAndWhere, Integer.class, args);
        return count == null ? 0 : count;
    }
}
