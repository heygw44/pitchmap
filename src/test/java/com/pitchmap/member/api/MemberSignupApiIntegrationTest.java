package com.pitchmap.member.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.domain.DisposableEmailDomain;
import com.pitchmap.member.domain.DisposableEmailDomainSource;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.domain.MemberStatus;
import com.pitchmap.member.infra.DisposableEmailDomainJpaRepository;
import com.pitchmap.member.infra.MemberJpaRepository;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@AutoConfigureMockMvc
class MemberSignupApiIntegrationTest {

    private static final String VALID_PASSWORD = "Valid-pass-1!";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private DisposableEmailDomainJpaRepository disposableRepository;

    @Test
    @DisplayName("[F-01] 가입하면 201을 응답하고 UNVERIFIED 회원이 소문자 이메일과 BCrypt 해시로 저장된다")
    void signUpPersistsUnverifiedMember() {
        String email = "Mixed." + TestSequence.next() + "@Example.COM";

        MvcTestResult result = post(email, VALID_PASSWORD, TestSequence.nickname());

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("UNVERIFIED");
        Member member = memberRepository.findAll().stream()
                .filter(m -> m.getEmail().equals(email.toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElseThrow();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.UNVERIFIED);
        assertThat(member.getPasswordHash()).isNotEqualTo(VALID_PASSWORD).startsWith("$2");
    }

    @Test
    @DisplayName("[F-01][EV-06] 대소문자만 다른 같은 이메일로 다시 가입하면 409를 응답한다")
    void duplicatedEmailIgnoringCaseReturnsConflict() {
        String email = "dup." + TestSequence.next() + "@example.com";
        post(email, VALID_PASSWORD, TestSequence.nickname());

        MvcTestResult result = post(email.toUpperCase(Locale.ROOT), VALID_PASSWORD, TestSequence.nickname());

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_EMAIL_DUPLICATED");
    }

    @Test
    @DisplayName("[F-01][EV-04] 일회용 이메일 도메인으로는 가입할 수 없다")
    void disposableEmailReturnsBadRequest() {
        disposableRepository.save(DisposableEmailDomain.of(
                "mailinator.com", DisposableEmailDomainSource.PUBLIC, MutableClock.DEFAULT_INSTANT));

        MvcTestResult result = post("x@mailinator.com", VALID_PASSWORD, TestSequence.nickname());

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_DISPOSABLE_EMAIL");
    }

    @Test
    @DisplayName("[F-01][PW-01] 약한 비밀번호면 400을 응답한다")
    void weakPasswordReturnsBadRequest() {
        MvcTestResult result = post(TestSequence.email(), "weak", TestSequence.nickname());

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_PASSWORD_POLICY");
    }

    @Test
    @DisplayName("[F-01] 닉네임이 중복이면 409를 응답한다")
    void duplicatedNicknameReturnsConflict() {
        String nickname = TestSequence.nickname();
        post(TestSequence.email(), VALID_PASSWORD, nickname);

        MvcTestResult result = post(TestSequence.email(), VALID_PASSWORD, nickname);

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NICKNAME_DUPLICATED");
    }

    @Test
    @DisplayName("[F-01] 성공 응답에는 이메일·비밀번호·해시가 없다")
    void successResponseHasNoSensitiveFields() {
        MvcTestResult result = post(TestSequence.email(), VALID_PASSWORD, TestSequence.nickname());

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().doesNotHavePath("$.email");
        assertThat(result).bodyJson().doesNotHavePath("$.password");
        assertThat(result).bodyJson().doesNotHavePath("$.passwordHash");
    }

    @Test
    @DisplayName("[F-01] 잘못된 입력의 오류 응답에는 traceId가 있다")
    void invalidInputResponseHasTraceId() {
        MvcTestResult result = post("not-an-email", VALID_PASSWORD, TestSequence.nickname());

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result).bodyJson().extractingPath("$.traceId").asString().isNotBlank();
    }

    private MvcTestResult post(String email, String password, String nickname) {
        String body = "{\"email\":\"%s\",\"password\":\"%s\",\"nickname\":\"%s\"}".formatted(email, password, nickname);
        return mvc.post()
                .uri("/api/members")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }
}
