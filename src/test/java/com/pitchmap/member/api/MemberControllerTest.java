package com.pitchmap.member.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pitchmap.common.error.GlobalExceptionHandler;
import com.pitchmap.common.security.SecurityConfig;
import com.pitchmap.common.trace.TraceIdFilter;
import com.pitchmap.member.application.MemberSignupService;
import com.pitchmap.member.application.SignupCommand;
import com.pitchmap.member.application.SignupResult;
import com.pitchmap.member.domain.MemberErrorCode;
import com.pitchmap.member.domain.MemberException;
import com.pitchmap.member.domain.MemberStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(MemberController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class})
class MemberControllerTest {

    private static final String VALID_PASSWORD = "Valid-pass-1!";
    // MockMvc가 요청에 기본으로 넣는 원격 주소
    private static final String MOCK_REMOTE_ADDR = "127.0.0.1";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private MemberSignupService memberSignupService;

    @Test
    @DisplayName("[F-01] 올바른 요청이면 201과 memberId, UNVERIFIED 상태를 응답하고 서비스에 요청 값을 넘긴다")
    void signUpReturnsCreated() {
        when(memberSignupService.signUp(any())).thenReturn(new SignupResult(12L, MemberStatus.UNVERIFIED));

        MvcTestResult result = post("hiker@example.com", VALID_PASSWORD, "hiker");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.memberId").isEqualTo(12);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("UNVERIFIED");
        verify(memberSignupService)
                .signUp(new SignupCommand("hiker@example.com", VALID_PASSWORD, "hiker", MOCK_REMOTE_ADDR));
    }

    @Test
    @DisplayName("[F-01][EV-06] 이메일이 중복이면 409와 오류 코드를 응답한다")
    void duplicatedEmailReturnsConflict() {
        when(memberSignupService.signUp(any())).thenThrow(new MemberException(MemberErrorCode.MEMBER_EMAIL_DUPLICATED));

        MvcTestResult result = post("hiker@example.com", VALID_PASSWORD, "hiker");

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_EMAIL_DUPLICATED");
    }

    @Test
    @DisplayName("[F-01] 닉네임이 중복이면 409를 응답한다")
    void duplicatedNicknameReturnsConflict() {
        when(memberSignupService.signUp(any()))
                .thenThrow(new MemberException(MemberErrorCode.MEMBER_NICKNAME_DUPLICATED));

        MvcTestResult result = post("hiker@example.com", VALID_PASSWORD, "hiker");

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NICKNAME_DUPLICATED");
    }

    @Test
    @DisplayName("[F-01][EV-04] 일회용 이메일이면 400을 응답한다")
    void disposableEmailReturnsBadRequest() {
        when(memberSignupService.signUp(any())).thenThrow(new MemberException(MemberErrorCode.MEMBER_DISPOSABLE_EMAIL));

        MvcTestResult result = post("hiker@example.com", VALID_PASSWORD, "hiker");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_DISPOSABLE_EMAIL");
    }

    @Test
    @DisplayName("[F-01][PW-01] 비밀번호 정책 위반이면 400을 응답한다")
    void passwordPolicyViolationReturnsBadRequest() {
        when(memberSignupService.signUp(any())).thenThrow(new MemberException(MemberErrorCode.MEMBER_PASSWORD_POLICY));

        MvcTestResult result = post("hiker@example.com", "weak", "hiker");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_PASSWORD_POLICY");
    }

    @Test
    @DisplayName("[F-01] 이메일 형식이 틀리면 email 필드 오류로 400을 응답하고 서비스를 부르지 않는다")
    void invalidEmailFormatReturnsFieldError() {
        MvcTestResult result = post("not-an-email", VALID_PASSWORD, "hiker");

        assertInvalidInput(result, "email");
    }

    @Test
    @DisplayName("[F-01] 이메일이 254자를 넘으면 400을 응답하고 서비스를 부르지 않는다")
    void tooLongEmailReturnsFieldError() {
        String email = "a".repeat(250) + "@example.com";

        MvcTestResult result = post(email, VALID_PASSWORD, "hiker");

        assertInvalidInput(result, "email");
    }

    @Test
    @DisplayName("[F-01] 닉네임이 1자면 nickname 필드 오류로 400을 응답한다")
    void tooShortNicknameReturnsFieldError() {
        MvcTestResult result = post("hiker@example.com", VALID_PASSWORD, "a");

        assertInvalidInput(result, "nickname");
    }

    @Test
    @DisplayName("[F-01] 닉네임이 21자면 nickname 필드 오류로 400을 응답한다")
    void tooLongNicknameReturnsFieldError() {
        MvcTestResult result = post("hiker@example.com", VALID_PASSWORD, "a".repeat(21));

        assertInvalidInput(result, "nickname");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("[F-01] 닉네임이 비었거나 공백뿐이면 nickname 필드 오류를 하나만 응답한다")
    void blankNicknameReportsSingleFieldError(String nickname) {
        MvcTestResult result = post("hiker@example.com", VALID_PASSWORD, nickname);

        assertInvalidInput(result, "nickname");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field=='nickname')]")
                .asList()
                .hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                " hiker",
                "hiker ",
                "hi ker ",
                "\\thiker",
                "hi  ker",
                "hi\u00A0ker",
                "hiker\u3164",
                "hi\u200Bker",
                "👨\u200D👩"
            })
    @DisplayName("[F-01] 닉네임에 앞뒤·연속 공백이나 보이지 않는 문자가 있으면 nickname 필드 오류로 400을 응답한다")
    void nicknameWithMisplacedSpacesOrHiddenCharactersReturnsFieldError(String nickname) {
        MvcTestResult result = post("hiker@example.com", VALID_PASSWORD, nickname);

        assertInvalidInput(result, "nickname");
    }

    @Test
    @DisplayName("[F-01] 닉네임 길이는 실제 글자 수로 센다: 이모지 20개는 받고 21개는 거부한다")
    void nicknameLengthCountsCodePoints() {
        when(memberSignupService.signUp(any())).thenReturn(new SignupResult(12L, MemberStatus.UNVERIFIED));

        MvcTestResult twenty = post("hiker@example.com", VALID_PASSWORD, "😀".repeat(20));

        assertThat(twenty).hasStatus(HttpStatus.CREATED);

        MvcTestResult twentyOne = post("hiker@example.com", VALID_PASSWORD, "😀".repeat(21));

        assertThat(twentyOne).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(twentyOne)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field=='nickname')]")
                .asList()
                .isNotEmpty();
    }

    @Test
    @DisplayName("[F-01] 닉네임이 이모지 1글자면 최소 길이에 못 미쳐 거부한다")
    void singleEmojiNicknameIsTooShort() {
        MvcTestResult result = post("hiker@example.com", VALID_PASSWORD, "😀");

        assertInvalidInput(result, "nickname");
    }

    @ParameterizedTest
    @ValueSource(strings = {"hiker@localhost", "hiker@[127.0.0.1]"})
    @DisplayName("[F-01][EV-06] 도메인에 점이 없거나 IP 주소인 이메일이면 email 필드 오류로 400을 응답한다")
    void emailWithoutMailableDomainReturnsFieldError(String email) {
        MvcTestResult result = post(email, VALID_PASSWORD, "hiker");

        assertInvalidInput(result, "email");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-email", "hiker@", "hiker@localhost", "hiker@[127.0.0.1]"})
    @DisplayName("[F-01] 이메일이 틀려도 email 필드 오류는 하나만 응답한다")
    void invalidEmailReportsSingleFieldError(String email) {
        MvcTestResult result = post(email, VALID_PASSWORD, "hiker");

        assertInvalidInput(result, "email");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field=='email')]")
                .asList()
                .hasSize(1);
    }

    @Test
    @DisplayName("[F-01] 비밀번호가 없으면 password 필드 오류로 400을 응답한다")
    void missingPasswordReturnsFieldError() {
        MvcTestResult result = mvc.post()
                .uri("/api/members")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"hiker@example.com\",\"passwordConfirm\":\"%s\",\"nickname\":\"hiker\"}"
                        .formatted(VALID_PASSWORD))
                .exchange();

        assertInvalidInput(result, "password");
    }

    @Test
    @DisplayName("[F-01][PW-01] 비밀번호 확인이 없으면 passwordConfirm 필드 오류로 400을 응답한다")
    void missingPasswordConfirmReturnsFieldError() {
        MvcTestResult result = mvc.post()
                .uri("/api/members")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"hiker@example.com\",\"password\":\"%s\",\"nickname\":\"hiker\"}"
                        .formatted(VALID_PASSWORD))
                .exchange();

        assertInvalidInput(result, "passwordConfirm");
    }

    @Test
    @DisplayName("[F-01][PW-01] 비밀번호 확인이 비밀번호와 다르면 passwordConfirm 필드 오류 하나로 400을 응답하고 서비스를 부르지 않는다")
    void mismatchedPasswordConfirmReturnsSingleFieldError() {
        MvcTestResult result = mvc.post()
                .uri("/api/members")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"hiker@example.com\",\"password\":\"%s\",\"passwordConfirm\":\"Other-pass-2!\","
                                .formatted(VALID_PASSWORD)
                        + "\"nickname\":\"hiker\"}")
                .exchange();

        assertInvalidInput(result, "passwordConfirm");
        assertThat(result).bodyJson().extractingPath("$.fieldErrors").asList().hasSize(1);
        assertThat(result).bodyJson().extractingPath("$.fieldErrors[0].field").isEqualTo("passwordConfirm");
    }

    @Test
    @DisplayName("[F-01] 요청 객체의 문자열 표현에는 이메일과 비밀번호가 없다")
    void requestToStringHidesSecrets() {
        SignupRequest request = new SignupRequest("hiker@example.com", VALID_PASSWORD, "Confirm-value-9?", "hiker");

        assertThat(request.toString())
                .doesNotContain("hiker@example.com")
                .doesNotContain(VALID_PASSWORD)
                .doesNotContain("Confirm-value-9?");
    }

    private void assertInvalidInput(MvcTestResult result, String field) {
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field=='" + field + "')]")
                .asList()
                .isNotEmpty();
        verifyNoInteractions(memberSignupService);
    }

    private MvcTestResult post(String email, String password, String nickname) {
        String body = "{\"email\":\"%s\",\"password\":\"%s\",\"passwordConfirm\":\"%s\",\"nickname\":\"%s\"}"
                .formatted(email, password, password, nickname);
        return mvc.post()
                .uri("/api/members")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }
}
