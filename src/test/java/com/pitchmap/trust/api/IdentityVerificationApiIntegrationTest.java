package com.pitchmap.trust.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.application.EmailVerificationService;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.trust.domain.CiHash;
import com.pitchmap.trust.domain.CiHasher;
import com.pitchmap.trust.domain.Gender;
import com.pitchmap.trust.domain.IdentityClaim;
import com.pitchmap.trust.domain.IdentityProvider;
import jakarta.servlet.http.Cookie;
import java.util.Map;
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
class IdentityVerificationApiIntegrationTest {

    private static final String PATH = "/api/me/identity-verification";
    private static final String VALID_PASSWORD = "Valid-pass1";
    private static final String SESSION_COOKIE = "SESSION";
    private static final String LOCAL_IP = "127.0.0.1";

    // 테스트 시계는 한국 날짜로 2026-10-05라서, 2007년생은 성인이고 2008년생은 미성년이다.
    private static final int ADULT_BIRTH_YEAR = 2007;
    private static final int MINOR_BIRTH_YEAR = 2008;

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
    private CiHasher ciHasher;

    @Autowired
    private IdentityProvider identityProvider;

    @Test
    @DisplayName("[F-11][ID-01][ID-02] 본인확인하면 200 {true, true, 1}이고, DB에는 출생연도·성별·CI 해시만 저장되고 CI 원값과 식별 문자열은 없다")
    void storesOnlyMinimalData() {
        // given
        Member member = saveMember();
        Cookie session = verifiedSession(member);

        // when
        MvcTestResult result = verify(session, ADULT_BIRTH_YEAR, "FEMALE", "demo-person-1");

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                { "identityVerified": true, "adult": true, "trustLevel": 1 }
                """);
        Map<String, Object> row =
                jdbc.queryForMap("SELECT * FROM identity_verification WHERE member_id = ?", member.getId());
        assertThat(row.get("birth_year")).isEqualTo(ADULT_BIRTH_YEAR);
        assertThat(row.get("gender")).isEqualTo("FEMALE");
        assertThat(row.get("provider")).isEqualTo("FAKE");
        assertThat(row.get("ci_retained_until")).isNull();
        String ciHash = (String) row.get("ci_hash");
        assertThat(ciHash).matches("[0-9a-f]{64}").doesNotContain("demo-person-1");
        CiHash expected = ciHasher.hash(identityProvider
                .verify(new IdentityClaim(ADULT_BIRTH_YEAR, Gender.FEMALE, "demo-person-1"))
                .ci());
        assertThat(ciHash).isEqualTo(expected.value());
        assertThat(row.values())
                .noneMatch(value -> value != null && value.toString().contains("demo-person-1"));
    }

    @Test
    @DisplayName("[ID-01] 이미 본인확인한 회원이 다시 요청하면 409 IDENTITY_ALREADY_VERIFIED이고 기록은 하나다")
    void secondRequestIsConflict() {
        Member member = saveMember();
        Cookie session = verifiedSession(member);
        verify(session, ADULT_BIRTH_YEAR, "FEMALE", "demo-person-1");

        MvcTestResult result = verify(session, ADULT_BIRTH_YEAR, "FEMALE", "demo-person-2");

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("IDENTITY_ALREADY_VERIFIED");
        assertThat(rowCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("[ID-04] 다른 회원이 같은 시연용 식별 문자열로 본인확인하면 409 IDENTITY_CI_DUPLICATED이고, 다른 문자열이면 성공한다")
    void sameKeyFromAnotherMemberIsConflict() {
        Cookie first = verifiedSession(saveMember());
        Cookie second = verifiedSession(saveMember());
        Cookie third = verifiedSession(saveMember());
        verify(first, ADULT_BIRTH_YEAR, "FEMALE", "demo-person-1");

        MvcTestResult duplicated = verify(second, ADULT_BIRTH_YEAR, "FEMALE", "demo-person-1");
        MvcTestResult other = verify(third, ADULT_BIRTH_YEAR, "MALE", "demo-person-3");

        assertThat(duplicated).hasStatus(HttpStatus.CONFLICT);
        assertThat(duplicated).bodyJson().extractingPath("$.code").isEqualTo("IDENTITY_CI_DUPLICATED");
        assertThat(other).hasStatus(HttpStatus.OK);
        assertThat(rowCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("[ID-03] 올해에서 18을 뺀 해에 태어난 회원은 200 {true, false, 0}이고 기록은 저장된다")
    void minorIsVerifiedButNotAdult() {
        Member member = saveMember();
        Cookie session = verifiedSession(member);

        MvcTestResult result = verify(session, MINOR_BIRTH_YEAR, "MALE", "demo-minor");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                { "identityVerified": true, "adult": false, "trustLevel": 0 }
                """);
        assertThat(rowCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-11] 출생연도가 범위를 벗어나거나 필드가 빠지면 400 INVALID_INPUT이고 저장하지 않는다")
    void invalidInputIsRejected() {
        Cookie session = verifiedSession(saveMember());

        MvcTestResult tooOld = verify(session, 1899, "FEMALE", "demo-1");
        MvcTestResult future = verify(session, 2027, "FEMALE", "demo-1");
        MvcTestResult badGender =
                send(session, "{\"birthYear\":1995,\"gender\":\"OTHER\",\"demoIdentityKey\":\"demo-1\"}");

        assertThat(tooOld).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(tooOld).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(future).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(badGender).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(rowCount()).isZero();
    }

    @Test
    @DisplayName("[TR-03] 이메일 인증 전인 회원은 403 MEMBER_NOT_VERIFIED이고 저장하지 않는다")
    void emailUnverifiedMemberIsForbidden() {
        Cookie session = login(saveMember());

        MvcTestResult result = verify(session, ADULT_BIRTH_YEAR, "FEMALE", "demo-1");

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        assertThat(rowCount()).isZero();
    }

    @Test
    @DisplayName("[F-11] 본인확인한 뒤 GET /api/me가 identityVerified와 trustLevel을 반영한다")
    void meReflectsVerification() throws Exception {
        Cookie session = verifiedSession(saveMember());
        verify(session, ADULT_BIRTH_YEAR, "FEMALE", "demo-1");

        MvcTestResult me = mvc.get().uri("/api/me").cookie(session).exchange();

        assertThat(me).hasStatus(HttpStatus.OK);
        assertThat(me).bodyJson().extractingPath("$.identityVerified").isEqualTo(true);
        assertThat(me).bodyJson().extractingPath("$.trustLevel").isEqualTo(1);
        assertThat(me.getResponse().getContentAsString())
                .doesNotContain("birthYear")
                .doesNotContain("ciHash");
    }

    private int rowCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM identity_verification", Integer.class);
    }

    private MvcTestResult verify(Cookie session, int birthYear, String gender, String demoIdentityKey) {
        return send(
                session,
                "{\"birthYear\":%d,\"gender\":\"%s\",\"demoIdentityKey\":\"%s\"}"
                        .formatted(birthYear, gender, demoIdentityKey));
    }

    private MvcTestResult send(Cookie session, String body) {
        return mvc.post()
                .uri(PATH)
                .with(csrf())
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private Member saveMember() {
        return memberRepository.saveAndFlush(aMember()
                .email(TestSequence.email())
                .passwordHash(passwordEncoder.encode(VALID_PASSWORD))
                .build());
    }

    private Cookie login(Member member) {
        MvcTestResult result = mvc.post()
                .uri("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(member.getEmail(), VALID_PASSWORD))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        Cookie session = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        return session;
    }

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
