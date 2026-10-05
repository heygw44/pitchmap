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
class MeApiIntegrationTest {

    private static final String ME_PATH = "/api/me";
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
    @DisplayName("[TR-03] 이메일 인증 전인 회원이 내 정보를 조회하면 200과 함께 9개 필드를 모두 응답한다")
    void unverifiedMemberReadsAllFields() {
        Member member = saveMember();
        Cookie session = login(member);

        MvcTestResult result = mvc.get().uri(ME_PATH).cookie(session).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {
                  "memberId": %d,
                  "email": "%s",
                  "nickname": "%s",
                  "status": "UNVERIFIED",
                  "role": "USER",
                  "selfAgeGroup": null,
                  "selfGender": null,
                  "identityVerified": false,
                  "trustLevel": 0
                }
                """.formatted(
                        member.getId(), member.getEmail(), member.getNickname()));
    }

    @Test
    @DisplayName("로그인하지 않고 내 정보를 조회하면 401 AUTHENTICATION_REQUIRED를 응답한다")
    void anonymousCannotReadMyInfo() {
        MvcTestResult result = mvc.get().uri(ME_PATH).exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    @DisplayName("[TR-03] 이메일 인증 전인 회원이 내 정보를 수정하면 403 MEMBER_NOT_VERIFIED를 응답하고 값은 그대로다")
    void unverifiedMemberCannotUpdateMyInfo() {
        Member member = saveMember();
        Cookie session = login(member);

        MvcTestResult result = patch(session, "{\"selfGender\":\"FEMALE\"}");

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        assertThat(column(member, "self_gender")).isNull();
    }

    @Test
    @DisplayName("인증 회원이 닉네임·연령대·성별을 바꾸면 200과 조회와 같은 형태로 응답하고, 다시 조회해도 바뀐 값이 나온다")
    void verifiedMemberUpdatesAllFields() {
        Member member = saveMember();
        Cookie session = verifiedSession(member);
        String newNickname = TestSequence.nickname();
        String expected = """
                {
                  "memberId": %d,
                  "email": "%s",
                  "nickname": "%s",
                  "status": "ACTIVE",
                  "role": "USER",
                  "selfAgeGroup": "SIXTIES_PLUS",
                  "selfGender": "FEMALE",
                  "identityVerified": false,
                  "trustLevel": 0
                }
                """.formatted(member.getId(), member.getEmail(), newNickname);

        MvcTestResult result = patch(
                session,
                "{\"nickname\":\"%s\",\"selfAgeGroup\":\"SIXTIES_PLUS\",\"selfGender\":\"FEMALE\"}"
                        .formatted(newNickname));

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo(expected);
        assertThat(mvc.get().uri(ME_PATH).cookie(session).exchange()).bodyJson().isStrictlyEqualTo(expected);
    }

    @Test
    @DisplayName("요청에 없는 필드는 바꾸지 않고, 보낸 필드만 바꾼다")
    void absentFieldsKeepTheirValues() {
        Member member = saveMember();
        Cookie session = verifiedSession(member);
        patch(session, "{\"selfAgeGroup\":\"THIRTIES\",\"selfGender\":\"MALE\"}");

        MvcTestResult result = patch(session, "{\"selfGender\":\"FEMALE\"}");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.nickname").isEqualTo(member.getNickname());
        assertThat(result).bodyJson().extractingPath("$.selfAgeGroup").isEqualTo("THIRTIES");
        assertThat(result).bodyJson().extractingPath("$.selfGender").isEqualTo("FEMALE");
        assertThat(column(member, "nickname")).isEqualTo(member.getNickname());
        assertThat(column(member, "self_age_group")).isEqualTo("THIRTIES");
    }

    @Test
    @DisplayName("자기 신고 성별을 null로 보내면 지우고, 함께 보내지 않은 연령대는 그대로 둔다")
    void explicitNullClearsSelfGender() {
        Member member = saveMember();
        Cookie session = verifiedSession(member);
        patch(session, "{\"selfAgeGroup\":\"TWENTIES\",\"selfGender\":\"FEMALE\"}");

        MvcTestResult result = patch(session, "{\"selfGender\":null}");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.selfGender").isNull();
        assertThat(result).bodyJson().extractingPath("$.selfAgeGroup").isEqualTo("TWENTIES");
        assertThat(column(member, "self_gender")).isNull();
        assertThat(column(member, "self_age_group")).isEqualTo("TWENTIES");
    }

    @Test
    @DisplayName("닉네임을 null로 보내면 지울 수 없어서 400 INVALID_INPUT을 응답하고 닉네임은 그대로다")
    void explicitNullNicknameIsRejected() {
        Member member = saveMember();
        Cookie session = verifiedSession(member);

        MvcTestResult result = patch(session, "{\"nickname\":null}");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(column(member, "nickname")).isEqualTo(member.getNickname());
    }

    @Test
    @DisplayName("허용 값 밖의 연령대를 보내면 400 INVALID_INPUT을 응답하고 값은 그대로다")
    void unknownSelfAgeGroupIsRejected() {
        Member member = saveMember();
        Cookie session = verifiedSession(member);

        MvcTestResult result = patch(session, "{\"selfAgeGroup\":\"SEVENTIES\"}");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(column(member, "self_age_group")).isNull();
    }

    @Test
    @DisplayName("다른 회원이 쓰는 닉네임으로 바꾸면 409 MEMBER_NICKNAME_DUPLICATED를 응답하고 닉네임은 그대로다")
    void nicknameOfAnotherMemberIsConflict() {
        Member other = saveMember();
        Member member = saveMember();
        Cookie session = verifiedSession(member);

        MvcTestResult result = patch(session, "{\"nickname\":\"%s\"}".formatted(other.getNickname()));

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NICKNAME_DUPLICATED");
        assertThat(column(member, "nickname")).isEqualTo(member.getNickname());
    }

    @Test
    @DisplayName("지금 쓰는 자기 닉네임을 그대로 보내면 중복으로 보지 않고 200을 응답한다")
    void ownCurrentNicknameIsAccepted() {
        Member member = saveMember();
        Cookie session = verifiedSession(member);

        MvcTestResult result = patch(session, "{\"nickname\":\"%s\"}".formatted(member.getNickname()));

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.nickname").isEqualTo(member.getNickname());
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
    // 코드 원값은 메일로만 나가므로, 테스트는 발송 처리기가 쓰는 서비스를 직접 불러 코드를 받는다.
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

    private MvcTestResult patch(Cookie session, String body) {
        return mvc.patch()
                .uri(ME_PATH)
                .with(csrf())
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private String column(Member member, String columnName) {
        return jdbc.queryForObject("SELECT " + columnName + " FROM member WHERE id = ?", String.class, member.getId());
    }
}
