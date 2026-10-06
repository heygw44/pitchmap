package com.pitchmap.member.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.application.EmailVerificationService;
import com.pitchmap.member.domain.EmailVerificationPolicy;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
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
class EmailVerificationApiIntegrationTest {

    private static final String VALID_PASSWORD = "Valid-pass1";
    private static final String SESSION_COOKIE = "SESSION";
    private static final String LOCAL_IP = "127.0.0.1";
    private static final String VERIFY_PATH = "/api/me/email-verification";
    private static final String RESEND_PATH = "/api/me/email-verification/resend";
    // 이메일 인증을 마친 회원만 부를 수 있는 경로다. 이 경로의 컨트롤러가 아직 없어도 인가는 컨트롤러보다 앞서 일어난다.
    private static final String VERIFIED_ONLY_PATH = "/api/bakjis";

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

    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName("[F-01] 로그인하지 않고 인증 코드 확인과 재발송을 호출하면 401 AUTHENTICATION_REQUIRED를 응답한다")
    void verifyAndResendRequireLogin() {
        MvcTestResult verify = postJson(VERIFY_PATH, "{\"code\":\"123456\"}", null);
        MvcTestResult resend = postJson(RESEND_PATH, null, null);

        assertThat(verify).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(verify).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(resend).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(resend).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    @DisplayName("[F-01][EV-01] 올바른 코드를 보내면 200 ACTIVE를 응답하고 회원 상태와 인증 행이 바뀐다")
    void correctCodeActivatesMember() {
        Member member = saveMember();
        Cookie session = login(member);
        String code = issueCode(member);

        MvcTestResult result = postJson(VERIFY_PATH, codeBody(code), session);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
        assertThat(memberStatus(member)).isEqualTo("ACTIVE");
        assertThat(count("email_verification WHERE member_id = ? AND verified_at IS NOT NULL", member.getId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("[F-01][TR-03] 인증에 성공하면 같은 세션으로 다시 로그인하지 않고 이메일 인증이 필요한 경로를 쓸 수 있다")
    void sessionGainsVerifiedAuthorityWithoutNewLogin() {
        Member member = saveMember();
        Cookie session = login(member);
        assertThat(postJson(VERIFIED_ONLY_PATH, "{}", session)).hasStatus(HttpStatus.FORBIDDEN);

        postJson(VERIFY_PATH, codeBody(issueCode(member)), session);

        MvcTestResult afterVerification = postJson(VERIFIED_ONLY_PATH, "{}", session);
        assertThat(afterVerification.getResponse().getStatus()).isNotIn(401, 403);
    }

    @Test
    @DisplayName("[F-01][TR-03] 인증 전에 다른 기기에서 만든 세션은 다시 로그인하기 전까지 인증 권한이 없다")
    void otherSessionOfSameMemberKeepsOldAuthorityUntilNextLogin() {
        Member member = saveMember();
        Cookie verifyingSession = login(member);
        Cookie otherSession = login(member);

        postJson(VERIFY_PATH, codeBody(issueCode(member)), verifyingSession);

        MvcTestResult stale = postJson(VERIFIED_ONLY_PATH, "{}", otherSession);
        assertThat(stale).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(stale).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        MvcTestResult afterRelogin = postJson(VERIFIED_ONLY_PATH, "{}", login(member));
        assertThat(afterRelogin.getResponse().getStatus()).isNotIn(401, 403);
    }

    @Test
    @DisplayName("[F-01][EV-01] 코드가 틀리면 400 EMAIL_CODE_INVALID를 응답하고 세션은 인증 권한을 얻지 못한다")
    void wrongCodeIsRejected() {
        Member member = saveMember();
        Cookie session = login(member);
        String code = issueCode(member);

        MvcTestResult result = postJson(VERIFY_PATH, codeBody(differentCode(code)), session);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("EMAIL_CODE_INVALID");
        assertThat(memberStatus(member)).isEqualTo("UNVERIFIED");
        assertThat(postJson(VERIFIED_ONLY_PATH, "{}", session)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("[F-01][EV-01] 코드 입력이 비어 있으면 400 INVALID_INPUT, 숫자 6자리가 아니면 400 EMAIL_CODE_INVALID를 응답한다")
    void blankAndMalformedCodes() {
        Member member = saveMember();
        Cookie session = login(member);
        issueCode(member);

        MvcTestResult blank = postJson(VERIFY_PATH, codeBody(" "), session);
        MvcTestResult malformed = postJson(VERIFY_PATH, codeBody("12ab56"), session);

        assertThat(blank).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(blank).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(malformed).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(malformed).bodyJson().extractingPath("$.code").isEqualTo("EMAIL_CODE_INVALID");
    }

    @Test
    @DisplayName("[F-01][EV-01] 발급한 지 10분이 지난 코드는 400 EMAIL_CODE_EXPIRED를 응답한다")
    void expiredCodeIsRejected() {
        Member member = saveMember();
        Cookie session = login(member);
        String code = issueCode(member);
        clock.advance(EmailVerificationPolicy.CODE_VALIDITY);

        MvcTestResult result = postJson(VERIFY_PATH, codeBody(code), session);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("EMAIL_CODE_EXPIRED");
        assertThat(memberStatus(member)).isEqualTo("UNVERIFIED");
    }

    @Test
    @DisplayName("[F-01][EV-02] 코드를 5번 틀리면 이후에는 맞는 코드를 보내도 400 EMAIL_CODE_ATTEMPTS_EXCEEDED를 응답한다")
    void codeBecomesInvalidAfterFiveWrongAttempts() {
        Member member = saveMember();
        Cookie session = login(member);
        String code = issueCode(member);
        String wrongCode = differentCode(code);
        for (int attempt = 0; attempt < EmailVerificationPolicy.MAX_ATTEMPTS; attempt++) {
            MvcTestResult wrong = postJson(VERIFY_PATH, codeBody(wrongCode), session);
            assertThat(wrong).bodyJson().extractingPath("$.code").isEqualTo("EMAIL_CODE_INVALID");
        }

        MvcTestResult result = postJson(VERIFY_PATH, codeBody(code), session);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("EMAIL_CODE_ATTEMPTS_EXCEEDED");
        assertThat(memberStatus(member)).isEqualTo("UNVERIFIED");
    }

    @Test
    @DisplayName("[F-01] 이미 인증을 마친 회원이 코드 확인이나 재발송을 호출하면 409 EMAIL_ALREADY_VERIFIED를 응답한다")
    void alreadyVerifiedMemberGetsConflict() {
        Member member = saveMember();
        Cookie session = login(member);
        String code = issueCode(member);
        assertThat(postJson(VERIFY_PATH, codeBody(code), session)).hasStatus(HttpStatus.OK);

        MvcTestResult verifyAgain = postJson(VERIFY_PATH, codeBody(code), session);
        MvcTestResult resend = postJson(RESEND_PATH, null, session);

        assertThat(verifyAgain).hasStatus(HttpStatus.CONFLICT);
        assertThat(verifyAgain).bodyJson().extractingPath("$.code").isEqualTo("EMAIL_ALREADY_VERIFIED");
        assertThat(resend).hasStatus(HttpStatus.CONFLICT);
        assertThat(resend).bodyJson().extractingPath("$.code").isEqualTo("EMAIL_ALREADY_VERIFIED");
    }

    @Test
    @DisplayName("[F-01][EV-03] 재발송하면 204를 응답하고 발송 요청 이벤트가 1건 생기며 이벤트에는 코드가 없다")
    void resendRecordsOneEventWithoutCode() {
        Member member = saveMember();
        Cookie session = login(member);

        MvcTestResult result = postJson(RESEND_PATH, null, session);

        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(count(
                        "outbox_event WHERE event_type = 'EMAIL_VERIFICATION_REQUESTED' AND aggregate_type = 'MEMBER'"
                                + " AND aggregate_id = ? AND status = 'PENDING'",
                        member.getId()))
                .isEqualTo(1);
        String payload = jdbc.queryForObject(
                "SELECT CAST(payload AS CHAR) FROM outbox_event WHERE aggregate_id = ?", String.class, member.getId());
        assertThat(payload).contains(LOCAL_IP).doesNotContainPattern("\\d{6}");
        assertThat(count("email_verification WHERE member_id = ?", member.getId()))
                .isZero();
    }

    @Test
    @DisplayName("[F-01][EV-03] 재발송 요청 뒤 60초 안에 다시 재발송하면 429 EMAIL_RESEND_LIMITED와 Retry-After를 응답한다")
    void resendWithinIntervalIsLimited() {
        Member member = saveMember();
        Cookie session = login(member);
        assertThat(postJson(RESEND_PATH, null, session)).hasStatus(HttpStatus.NO_CONTENT);

        MvcTestResult result = postJson(RESEND_PATH, null, session);

        assertThat(result).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("EMAIL_RESEND_LIMITED");
        assertThat(result.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
        assertThat(count("outbox_event WHERE aggregate_id = ?", member.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-01][EV-03] 하루에 5번 발송한 뒤에는 간격이 지나도 429 EMAIL_RESEND_LIMITED를 응답하고 풀리는 시각까지 기다리게 한다")
    void resendAfterDailyLimitIsLimited() {
        Member member = saveMember();
        Cookie session = login(member);
        for (int sent = 0; sent < EmailVerificationPolicy.DAILY_LIMIT; sent++) {
            issueCode(member);
            clock.advance(EmailVerificationPolicy.RESEND_INTERVAL.plusSeconds(1));
        }

        MvcTestResult result = postJson(RESEND_PATH, null, session);

        assertThat(result).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("EMAIL_RESEND_LIMITED");
        long retryAfterSeconds = Long.parseLong(result.getResponse().getHeader(HttpHeaders.RETRY_AFTER));
        assertThat(retryAfterSeconds)
                .isGreaterThan(EmailVerificationPolicy.RESEND_INTERVAL.toSeconds())
                .isLessThanOrEqualTo(EmailVerificationPolicy.DAILY_WINDOW.toSeconds());
        assertThat(count("outbox_event WHERE aggregate_id = ?", member.getId())).isZero();
    }

    @Test
    @DisplayName("[F-01][ADR-002] CSRF 토큰 없이 코드 확인이나 재발송을 호출하면 403 ACCESS_DENIED를 응답한다")
    void requestsWithoutCsrfTokenAreForbidden() {
        Member member = saveMember();
        Cookie session = login(member);

        MvcTestResult verify = mvc.post()
                .uri(VERIFY_PATH)
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(codeBody("123456"))
                .exchange();
        MvcTestResult resend = mvc.post().uri(RESEND_PATH).cookie(session).exchange();

        assertThat(verify).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(verify).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        assertThat(resend).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(resend).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
    }

    private Member saveMember() {
        return memberRepository.save(aMember()
                .email(TestSequence.email())
                .passwordHash(passwordEncoder.encode(VALID_PASSWORD))
                .build());
    }

    // 코드 원값은 발송 처리기가 메일을 보내기 직전에 만들어 메모리에서 메일로만 나간다. 테스트는 같은 서비스를 직접 불러 그 값을 받는다.
    private String issueCode(Member member) {
        return emailVerificationService
                .issueFor(member.getId(), LOCAL_IP)
                .orElseThrow()
                .code();
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

    private MvcTestResult postJson(String path, String body, Cookie session) {
        MockMvcTester.MockMvcRequestBuilder request = mvc.post().uri(path).with(csrf());
        if (session != null) {
            request.cookie(session);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return request.exchange();
    }

    private static String codeBody(String code) {
        return "{\"code\":\"%s\"}".formatted(code);
    }

    // 맞는 코드와 첫 자리만 다른 6자리 숫자다. 임의의 틀린 코드를 쓰면 아주 드물게 맞는 코드와 같아져서 테스트가 흔들린다.
    private static String differentCode(String code) {
        char first = code.charAt(0);
        char changed = first == '9' ? '0' : (char) (first + 1);
        return changed + code.substring(1);
    }

    private String memberStatus(Member member) {
        return jdbc.queryForObject("SELECT status FROM member WHERE id = ?", String.class, member.getId());
    }

    private int count(String fromAndWhere, Object... args) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + fromAndWhere, Integer.class, args);
        return count == null ? 0 : count;
    }
}
