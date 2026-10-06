package com.pitchmap.trust.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.application.EmailVerificationService;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
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
class MemberProfileApiIntegrationTest {

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
    @DisplayName("[NFR-11] 같은 회원의 프로필을 비회원은 닉네임만, 다른 회원·본인·관리자는 연령대·성별·신뢰 정보까지 본다")
    void viewsDifferByRequester() {
        Member target = saveMember();
        Cookie targetSession = verifiedSession(target);
        verifyIdentity(targetSession, 1990, "demo-target");
        Cookie other = login(saveMember());
        Member admin = saveMember();
        jdbc.update("UPDATE member SET status = 'ACTIVE', role = 'ADMIN' WHERE id = ?", admin.getId());
        Cookie adminSession = login(admin);
        String path = pathOf(target);

        MvcTestResult anonymous = mvc.get().uri(path).exchange();
        MvcTestResult byOther = mvc.get().uri(path).cookie(other).exchange();
        MvcTestResult bySelf = mvc.get().uri(path).cookie(targetSession).exchange();
        MvcTestResult byAdmin = mvc.get().uri(path).cookie(adminSession).exchange();

        assertThat(anonymous).hasStatus(HttpStatus.OK);
        assertThat(anonymous).bodyJson().isStrictlyEqualTo("""
                { "memberId": %d, "nickname": "%s" }
                """.formatted(target.getId(), target.getNickname()));
        String memberView = """
                {
                  "memberId": %d,
                  "nickname": "%s",
                  "ageGroup": "THIRTIES",
                  "ageGroupVerified": true,
                  "gender": "FEMALE",
                  "genderVerified": true,
                  "trustLevel": 1,
                  "completedCompanions": 0,
                  "companionReviewSummary": { "rejoinRate": null, "topTags": [] }
                }
                """.formatted(target.getId(), target.getNickname());
        for (MvcTestResult result : new MvcTestResult[] {byOther, bySelf, byAdmin}) {
            assertThat(result).hasStatus(HttpStatus.OK);
            assertThat(result).bodyJson().isStrictlyEqualTo(memberView);
        }
    }

    @Test
    @DisplayName("[NFR-11] 어느 요청자의 응답에도 이메일, 출생연도, CI 해시 필드가 없다")
    void neverExposesSensitiveFields() {
        Member target = saveMember();
        Cookie targetSession = verifiedSession(target);
        verifyIdentity(targetSession, 1990, "demo-target");
        Cookie other = login(saveMember());
        String path = pathOf(target);

        for (MvcTestResult result : new MvcTestResult[] {
            mvc.get().uri(path).exchange(),
            mvc.get().uri(path).cookie(other).exchange(),
            mvc.get().uri(path).cookie(targetSession).exchange()
        }) {
            String body = bodyOf(result);
            assertThat(body)
                    .doesNotContain("email", "birthYear", "birth_year", "ciHash", "ci_hash")
                    .doesNotContain(target.getEmail());
        }
    }

    @Test
    @DisplayName("[NFR-11] 본인확인이 없으면 자기 신고 값을 verified false로 응답하고, 신고하지 않은 값은 null이다")
    void withoutVerificationUsesSelfReportedValues() {
        Member target = saveMember();
        jdbc.update("UPDATE member SET self_age_group = 'FORTIES' WHERE id = ?", target.getId());
        Cookie viewer = login(saveMember());

        MvcTestResult result = mvc.get().uri(pathOf(target)).cookie(viewer).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {
                  "memberId": %d,
                  "nickname": "%s",
                  "ageGroup": "FORTIES",
                  "ageGroupVerified": false,
                  "gender": null,
                  "genderVerified": false,
                  "trustLevel": 0,
                  "completedCompanions": 0,
                  "companionReviewSummary": { "rejoinRate": null, "topTags": [] }
                }
                """.formatted(target.getId(), target.getNickname()));
    }

    @Test
    @DisplayName("[NFR-11] 본인확인한 성인은 자기 신고 값이 달라도 본인확인한 값(verified true)을 응답한다")
    void verifiedValuesWinOverSelfReported() {
        Member target = saveMember();
        jdbc.update("UPDATE member SET self_age_group = 'TWENTIES', self_gender = 'MALE' WHERE id = ?", target.getId());
        verifyIdentity(verifiedSession(target), 1990, "demo-target");
        Cookie viewer = login(saveMember());

        MvcTestResult result = mvc.get().uri(pathOf(target)).cookie(viewer).exchange();

        assertThat(result).bodyJson().extractingPath("$.ageGroup").isEqualTo("THIRTIES");
        assertThat(result).bodyJson().extractingPath("$.ageGroupVerified").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.gender").isEqualTo("FEMALE");
        assertThat(result).bodyJson().extractingPath("$.genderVerified").isEqualTo(true);
    }

    @Test
    @DisplayName("[NFR-11][ID-03] 본인확인한 미성년은 연령대로 자기 신고 값(verified false)을, 성별로 본인확인 값을 응답한다")
    void verifiedMinorUsesSelfReportedAgeGroup() {
        Member target = saveMember();
        jdbc.update("UPDATE member SET self_age_group = 'TWENTIES' WHERE id = ?", target.getId());
        // 테스트 시계는 한국 날짜로 2026-10-05라서 2008년생은 미성년이다.
        verifyIdentity(verifiedSession(target), 2008, "demo-minor");
        Cookie viewer = login(saveMember());

        MvcTestResult result = mvc.get().uri(pathOf(target)).cookie(viewer).exchange();

        assertThat(result).bodyJson().extractingPath("$.ageGroup").isEqualTo("TWENTIES");
        assertThat(result).bodyJson().extractingPath("$.ageGroupVerified").isEqualTo(false);
        assertThat(result).bodyJson().extractingPath("$.gender").isEqualTo("FEMALE");
        assertThat(result).bodyJson().extractingPath("$.genderVerified").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.trustLevel").isEqualTo(0);
    }

    @Test
    @DisplayName("없는 회원의 프로필은 비회원·회원 모두 404 NOT_FOUND다")
    void missingMemberIsNotFound() {
        Cookie viewer = login(saveMember());

        MvcTestResult anonymous =
                mvc.get().uri("/api/members/999999999/profile").exchange();
        MvcTestResult loggedIn =
                mvc.get().uri("/api/members/999999999/profile").cookie(viewer).exchange();

        assertThat(anonymous).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(anonymous).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(loggedIn).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(loggedIn).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("탈퇴한 회원의 프로필은 404 NOT_FOUND다")
    void withdrawnMemberIsNotFound() {
        Member target = saveMember();
        jdbc.update("UPDATE member SET status = 'WITHDRAWN' WHERE id = ?", target.getId());
        Cookie viewer = login(saveMember());

        MvcTestResult anonymous = mvc.get().uri(pathOf(target)).exchange();
        MvcTestResult loggedIn = mvc.get().uri(pathOf(target)).cookie(viewer).exchange();

        assertThat(anonymous).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(loggedIn).hasStatus(HttpStatus.NOT_FOUND);
    }

    private static String pathOf(Member member) {
        return "/api/members/" + member.getId() + "/profile";
    }

    private static String bodyOf(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
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
