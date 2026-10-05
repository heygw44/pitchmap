package com.pitchmap.member.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.infra.MemberJpaRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@IntegrationTest
@AutoConfigureMockMvc
class PasswordResetRequestLimitApiIntegrationTest {

    private static final String REQUEST_PATH = "/api/auth/password-reset/request";
    private static final String LIMITED_CODE = "PASSWORD_RESET_LIMITED";
    private static final String CLIENT_IP = "203.0.113.7";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName("[F-02][PW-05] 같은 이메일로 곧바로 다시 요청하면 429 PASSWORD_RESET_LIMITED이고 Retry-After가 남은 초다")
    void secondRequestIsLimitedWithRetryAfter() {
        // given
        String email = TestSequence.email();
        assertThat(postRequest(email, CLIENT_IP)).hasStatus(HttpStatus.NO_CONTENT);
        clock.advance(Duration.ofSeconds(20));

        // when
        MvcTestResult limited = postRequest(email, CLIENT_IP);

        // then
        assertThat(limited).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(limited).bodyJson().extractingPath("$.code").isEqualTo(LIMITED_CODE);
        String retryAfter = limited.getResponse().getHeader(HttpHeaders.RETRY_AFTER);
        assertThat(retryAfter).matches("\\d+");
        assertThat(Long.parseLong(retryAfter)).isBetween(1L, 60L).isEqualTo(40L);
    }

    @Test
    @DisplayName("[F-02][PW-05] 가입된 이메일과 없는 이메일이 같은 상태 순서(204, 429)와 같은 응답 모양을 받는다")
    void registeredAndUnknownEmailGetSameResponses() {
        // given
        Member member = memberRepository.save(
                aMember().email(TestSequence.email()).now(clock.instant()).build());
        String unknownEmail = TestSequence.email();

        // when
        List<MvcTestResult> registered =
                List.of(postRequest(member.getEmail(), CLIENT_IP), postRequest(member.getEmail(), CLIENT_IP));
        List<MvcTestResult> unknown =
                List.of(postRequest(unknownEmail, CLIENT_IP), postRequest(unknownEmail, CLIENT_IP));

        // then
        assertThat(registered.get(0)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(registered.get(1)).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(unknown.get(0)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(unknown.get(1)).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(bodyOf(unknown.get(0))).isEqualTo(bodyOf(registered.get(0))).isEmpty();
        assertThat(bodyKeys(unknown.get(1))).isEqualTo(bodyKeys(registered.get(1)));
        assertThat(JsonPath.<String>read(bodyOf(unknown.get(1)), "$.code"))
                .isEqualTo(JsonPath.<String>read(bodyOf(registered.get(1)), "$.code"))
                .isEqualTo(LIMITED_CODE);
        assertThat(JsonPath.<String>read(bodyOf(unknown.get(1)), "$.message"))
                .isEqualTo(JsonPath.<String>read(bodyOf(registered.get(1)), "$.message"));
        assertThat(unknown.get(1).getResponse().getHeader(HttpHeaders.RETRY_AFTER))
                .isEqualTo(registered.get(1).getResponse().getHeader(HttpHeaders.RETRY_AFTER));
        assertThat(count("outbox_event")).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-02][PW-05] 시계를 60초 움직이면 같은 이메일이 다시 204를 받는다")
    void requestPassesAgainAfterInterval() {
        // given
        String email = TestSequence.email();
        assertThat(postRequest(email, CLIENT_IP)).hasStatus(HttpStatus.NO_CONTENT);
        clock.advance(Duration.ofSeconds(59));
        assertThat(postRequest(email, CLIENT_IP)).hasStatus(HttpStatus.TOO_MANY_REQUESTS);

        // when
        clock.advance(Duration.ofSeconds(1));
        MvcTestResult result = postRequest(email, CLIENT_IP);

        // then
        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("[F-02][PW-05] 같은 IP로 서로 다른 이메일 21개를 보내면 21번째가 429이고 Retry-After가 1시간 안이다")
    void twentyFirstRequestFromSameIpIsLimited() {
        // given
        List<MvcTestResult> results = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            results.add(postRequest(TestSequence.email(), CLIENT_IP));
        }

        // when
        MvcTestResult twentyFirst = postRequest(TestSequence.email(), CLIENT_IP);

        // then
        assertThat(results).allSatisfy(result -> assertThat(result).hasStatus(HttpStatus.NO_CONTENT));
        assertThat(twentyFirst).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(twentyFirst).bodyJson().extractingPath("$.code").isEqualTo(LIMITED_CODE);
        assertThat(Long.parseLong(twentyFirst.getResponse().getHeader(HttpHeaders.RETRY_AFTER)))
                .isBetween(1L, Duration.ofHours(1).toSeconds());
        assertThat(postRequest(TestSequence.email(), "198.51.100.9")).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("[F-02][PW-05] CSRF 토큰이 없으면 403이고 한도에 세지 않는다")
    void missingCsrfIsForbiddenAndNotCounted() {
        // given
        String email = TestSequence.email();

        // when
        MvcTestResult forbidden = mvc.post()
                .uri(REQUEST_PATH)
                .with(remoteAddress(CLIENT_IP))
                .contentType(MediaType.APPLICATION_JSON)
                .content(emailBody(email))
                .exchange();

        // then
        assertThat(forbidden).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(count("password_reset_throttle")).isZero();
        assertThat(postRequest(email, CLIENT_IP)).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("[F-02][PW-05] 형식이 틀린 이메일은 400이고 한도에 세지 않는다")
    void malformedEmailIsBadRequestAndNotCounted() {
        // when
        MvcTestResult malformed = postRequest("not-an-email", CLIENT_IP);

        // then
        assertThat(malformed).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(count("password_reset_throttle")).isZero();
        assertThat(postRequest(TestSequence.email(), CLIENT_IP)).hasStatus(HttpStatus.NO_CONTENT);
    }

    private MvcTestResult postRequest(String email, String remoteAddress) {
        return mvc.post()
                .uri(REQUEST_PATH)
                .with(csrf())
                .with(remoteAddress(remoteAddress))
                .contentType(MediaType.APPLICATION_JSON)
                .content(emailBody(email))
                .exchange();
    }

    private static RequestPostProcessor remoteAddress(String remoteAddress) {
        return request -> {
            request.setRemoteAddr(remoteAddress);
            return request;
        };
    }

    private static String emailBody(String email) {
        return "{\"email\":\"%s\"}".formatted(email);
    }

    private static String bodyOf(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private static Object bodyKeys(MvcTestResult result) {
        Map<String, Object> body = JsonPath.read(bodyOf(result), "$");
        return body.keySet();
    }

    private int count(String table) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }
}
