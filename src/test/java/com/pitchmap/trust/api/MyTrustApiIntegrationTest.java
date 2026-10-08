package com.pitchmap.trust.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.application.EmailVerificationService;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.trust.application.CompanionReviewFixture;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
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
class MyTrustApiIntegrationTest {

    private static final String PATH = "/api/me/trust";

    private static final String VALID_PASSWORD = "Valid-pass1";
    private static final String SESSION_COOKIE = "SESSION";
    private static final String LOCAL_IP = "127.0.0.1";

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
    @DisplayName("로그인하지 않고 내 신뢰 단계를 조회하면 401 AUTHENTICATION_REQUIRED를 응답한다")
    void anonymousIsUnauthorized() {
        MvcTestResult result = mvc.get().uri(PATH).exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    @DisplayName("[TR-01] 본인확인 전인 회원은 단계 0이고, 단계 2 조건의 현재 값은 0회와 null이다")
    void beforeIdentityVerificationIsLevelZero() {
        Cookie session = login(saveMember());

        MvcTestResult result = mvc.get().uri(PATH).cookie(session).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo(expected(0, false));
    }

    @Test
    @DisplayName("[TR-01] 본인확인을 마친 성인은 단계 1이고 nextLevel이 단계 2 조건을 알려 준다")
    void adultIsLevelOne() {
        Cookie session = verifiedSession(saveMember());
        // 테스트 시계는 한국 날짜로 2026-10-05라서, 2007년생은 성인이고 2008년생은 미성년이다.
        verifyIdentity(session, 2007, "demo-adult");

        MvcTestResult result = mvc.get().uri(PATH).cookie(session).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo(expected(1, true));
    }

    @Test
    @DisplayName("[TR-01][ID-03] 본인확인을 마친 미성년은 단계 0이다")
    void minorIsLevelZero() {
        Cookie session = verifiedSession(saveMember());
        verifyIdentity(session, 2008, "demo-minor");

        MvcTestResult result = mvc.get().uri(PATH).cookie(session).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo(expected(0, true));
    }

    @Test
    @DisplayName("[TR-01] 완료 동행 3회와 다시 동행 80%를 채운 성인은 단계 2이고 nextLevel 필드가 없다")
    void trustedAdultIsLevelTwoWithoutNextLevel() {
        Member member = saveMember();
        Cookie session = verifiedSession(member);
        verifyIdentity(session, 2007, "demo-trusted");
        new CompanionReviewFixture(jdbc, memberRepository)
                .insertQualifiedRecord(member.getId(), MutableClock.DEFAULT_INSTANT.minus(Duration.ofDays(20)));

        MvcTestResult result = mvc.get().uri(PATH).cookie(session).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                { "trustLevel": 2, "identityVerified": true }
                """);
    }

    @Test
    @DisplayName("[BC-21] 임박 탈퇴 3회면 다른 조건을 채워도 단계 1이고 recentEarlyLeaves가 현재 3회와 한도 3을 알려 준다")
    void earlyLeavesKeepLevelOne() {
        Member member = saveMember();
        Cookie session = verifiedSession(member);
        verifyIdentity(session, 2007, "demo-early-leaver");
        CompanionReviewFixture fixture = new CompanionReviewFixture(jdbc, memberRepository);
        Instant now = MutableClock.DEFAULT_INSTANT;
        fixture.insertQualifiedRecord(member.getId(), now.minus(Duration.ofDays(20)));
        for (int i = 1; i <= 3; i++) {
            fixture.insertEarlyLeaver(
                    fixture.saveBasecamp("CONFIRMED", null), member.getId(), now.minus(Duration.ofDays(i)));
        }

        MvcTestResult result = mvc.get().uri(PATH).cookie(session).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {
                  "trustLevel": 1,
                  "identityVerified": true,
                  "nextLevel": {
                    "level": 2,
                    "completedCompanions": { "current": 3, "required": 3 },
                    "rejoinRate": { "current": 80, "required": 80 },
                    "recentEarlyLeaves": { "current": 3, "limit": 3 },
                    "noRecentSanction": true
                  }
                }
                """);
    }

    private static String expected(int trustLevel, boolean identityVerified) {
        return """
                {
                  "trustLevel": %d,
                  "identityVerified": %b,
                  "nextLevel": {
                    "level": 2,
                    "completedCompanions": { "current": 0, "required": 3 },
                    "rejoinRate": { "current": null, "required": 80 },
                    "recentEarlyLeaves": { "current": 0, "limit": 3 },
                    "noRecentSanction": true
                  }
                }
                """.formatted(trustLevel, identityVerified);
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

    private void verifyIdentity(Cookie session, int birthYear, String demoIdentityKey) {
        MvcTestResult result = mvc.post()
                .uri("/api/me/identity-verification")
                .with(csrf())
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"birthYear\":%d,\"gender\":\"FEMALE\",\"demoIdentityKey\":\"%s\"}"
                        .formatted(birthYear, demoIdentityKey))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
    }
}
