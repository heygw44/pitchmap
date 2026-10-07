package com.pitchmap.basecamp.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.pitchmap.basecamp.application.BasecampOpenCommand;
import com.pitchmap.basecamp.application.BasecampOpenCommand.JoinConditionCommand;
import com.pitchmap.basecamp.application.BasecampOpenResult;
import com.pitchmap.basecamp.application.BasecampOpenService;
import com.pitchmap.basecamp.domain.BasecampErrorCode;
import com.pitchmap.basecamp.domain.BasecampException;
import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.error.GlobalExceptionHandler;
import com.pitchmap.common.security.LoginMember;
import com.pitchmap.common.security.SecurityConfig;
import com.pitchmap.common.trace.TraceIdFilter;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(BasecampController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class})
class BasecampControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final String BASECAMPS = "/api/basecamps";
    private static final String VALID_BODY = body("\"title\":\"북한산 백패킹\"", "\"capacity\":4");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private BasecampOpenService basecampOpenService;

    @Test
    @DisplayName("[F-12] 베이스캠프를 열면 201과 basecampId·status를 응답하고, 요청 값과 로그인한 회원의 ID를 서비스에 넘긴다")
    void openReturnsCreated() {
        // given
        when(basecampOpenService.open(eq(MEMBER_ID), any())).thenReturn(new BasecampOpenResult(55L, "RECRUITING"));

        // when
        MvcTestResult result = post(verified(), VALID_BODY);

        // then
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().isStrictlyEqualTo("{ \"basecampId\": 55, \"status\": \"RECRUITING\" }");
        ArgumentCaptor<BasecampOpenCommand> captor = ArgumentCaptor.forClass(BasecampOpenCommand.class);
        verify(basecampOpenService).open(eq(MEMBER_ID), captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(new BasecampOpenCommand(
                        101L, "북한산 백패킹", "함께 가요", LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 22), 4, null));
    }

    @Test
    @DisplayName("[F-12][BC-05] 합류 조건을 보내면 그대로 서비스에 넘긴다")
    void joinConditionIsPassedToService() {
        // given
        when(basecampOpenService.open(eq(MEMBER_ID), any())).thenReturn(new BasecampOpenResult(55L, "RECRUITING"));
        String joinCondition =
                "\"joinCondition\":{\"minTrustLevel\":2,\"ageGroupMin\":20,\"ageGroupMax\":30,\"sameGenderOnly\":true}";

        // when
        MvcTestResult result = post(verified(), body("\"title\":\"북한산 백패킹\"", "\"capacity\":4", joinCondition));

        // then
        assertThat(result).hasStatus(HttpStatus.CREATED);
        ArgumentCaptor<BasecampOpenCommand> captor = ArgumentCaptor.forClass(BasecampOpenCommand.class);
        verify(basecampOpenService).open(eq(MEMBER_ID), captor.capture());
        assertThat(captor.getValue().joinCondition()).isEqualTo(new JoinConditionCommand(2, 20, 30, true));
    }

    @Test
    @DisplayName("[F-12] 제목이 100자이면 받고, 101자이면 title 필드 오류로 400 INVALID_INPUT을 응답한다")
    void titleLengthBoundary() {
        // given
        when(basecampOpenService.open(eq(MEMBER_ID), any())).thenReturn(new BasecampOpenResult(1L, "RECRUITING"));

        // when
        MvcTestResult atLimit = post(verified(), body("\"title\":\"" + "가".repeat(100) + "\"", "\"capacity\":4"));
        MvcTestResult overLimit = post(verified(), body("\"title\":\"" + "가".repeat(101) + "\"", "\"capacity\":4"));

        // then
        assertThat(atLimit).hasStatus(HttpStatus.CREATED);
        assertThat(overLimit).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(overLimit).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(overLimit)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field=='title')]")
                .asList()
                .hasSize(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bodiesWithMissingRequiredValue")
    @DisplayName("[F-12] 필수 값이 없거나 비어 있으면 400 INVALID_INPUT을 응답하고 서비스를 부르지 않는다")
    void openRejectsMissingRequiredValue(String description, String requestBody) {
        // when
        MvcTestResult result = post(verified(), requestBody);

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        verifyNoInteractions(basecampOpenService);
    }

    static Stream<Arguments> bodiesWithMissingRequiredValue() {
        return Stream.of(
                Arguments.of(
                        "장소 없음",
                        "{" + String.join(",", "\"title\":\"가\"", "\"description\":\"나\"", dates(), "\"capacity\":4")
                                + "}"),
                Arguments.of(
                        "제목 없음",
                        "{" + String.join(",", "\"spotId\":101", "\"description\":\"나\"", dates(), "\"capacity\":4")
                                + "}"),
                Arguments.of("제목 공백", body("\"title\":\"   \"", "\"capacity\":4")),
                Arguments.of(
                        "설명 없음",
                        "{" + String.join(",", "\"spotId\":101", "\"title\":\"가\"", dates(), "\"capacity\":4") + "}"),
                Arguments.of(
                        "출발일 없음",
                        "{"
                                + String.join(
                                        ",",
                                        "\"spotId\":101",
                                        "\"title\":\"가\"",
                                        "\"description\":\"나\"",
                                        "\"endDate\":\"2026-10-22\"",
                                        "\"capacity\":4")
                                + "}"),
                Arguments.of(
                        "종료일 없음",
                        "{"
                                + String.join(
                                        ",",
                                        "\"spotId\":101",
                                        "\"title\":\"가\"",
                                        "\"description\":\"나\"",
                                        "\"startDate\":\"2026-10-20\"",
                                        "\"capacity\":4")
                                + "}"),
                Arguments.of(
                        "정원 없음",
                        "{" + String.join(",", "\"spotId\":101", "\"title\":\"가\"", "\"description\":\"나\"", dates())
                                + "}"));
    }

    @Test
    @DisplayName("[F-12] 로그인하지 않은 사용자가 열면 401 AUTHENTICATION_REQUIRED를 응답한다")
    void anonymousIsUnauthorized() {
        // when
        MvcTestResult result = mvc.post()
                .uri(BASECAMPS)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
        verifyNoInteractions(basecampOpenService);
    }

    @Test
    @DisplayName("[F-12][TR-03] 이메일 인증 전의 회원이 열면 403 MEMBER_NOT_VERIFIED를 응답한다")
    void unverifiedMemberIsForbidden() {
        // when
        MvcTestResult result = post(unverified(), VALID_BODY);

        // then
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        verifyNoInteractions(basecampOpenService);
    }

    @Test
    @DisplayName("[F-12] CSRF 토큰 없이 열면 403을 응답하고 서비스를 부르지 않는다")
    void missingCsrfIsForbidden() {
        // when
        MvcTestResult result = mvc.post()
                .uri(BASECAMPS)
                .with(verified())
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        verifyNoInteractions(basecampOpenService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("serviceErrors")
    @DisplayName("[F-12] 서비스가 던진 오류는 코드와 상태 그대로 응답한다")
    void serviceErrorsAreMapped(BusinessException error, HttpStatus status) {
        // given
        when(basecampOpenService.open(eq(MEMBER_ID), any())).thenThrow(error);

        // when
        MvcTestResult result = post(verified(), VALID_BODY);

        // then
        assertThat(result).hasStatus(status);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo(error.getErrorCode().name());
    }

    static Stream<Arguments> serviceErrors() {
        return Stream.of(
                Arguments.of(new BusinessException(CommonErrorCode.TRUST_LEVEL_INSUFFICIENT), HttpStatus.FORBIDDEN),
                Arguments.of(new BusinessException(CommonErrorCode.NOT_FOUND), HttpStatus.NOT_FOUND),
                Arguments.of(new BasecampException(BasecampErrorCode.BASECAMP_OPEN_LIMIT), HttpStatus.BAD_REQUEST),
                Arguments.of(
                        new BasecampException(BasecampErrorCode.BASECAMP_SCHEDULE_INVALID), HttpStatus.BAD_REQUEST),
                Arguments.of(new BasecampException(BasecampErrorCode.BASECAMP_WARNING_SPOT), HttpStatus.BAD_REQUEST),
                Arguments.of(
                        new BasecampException(BasecampErrorCode.BASECAMP_CAPACITY_INVALID), HttpStatus.BAD_REQUEST));
    }

    private static String dates() {
        return "\"startDate\":\"2026-10-20\",\"endDate\":\"2026-10-22\"";
    }

    private static String body(String... overrides) {
        return "{\"spotId\":101,\"description\":\"함께 가요\"," + dates() + "," + String.join(",", overrides) + "}";
    }

    private MvcTestResult post(RequestPostProcessor login, String requestBody) {
        return mvc.post()
                .uri(BASECAMPS)
                .with(login)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .exchange();
    }

    // 로그인 서비스가 세션에 넣는 것과 같은 모양의 인증 정보를 만든다. 이메일 인증 권한을 붙여 인증 회원 전용 경로를 연다.
    private static RequestPostProcessor verified() {
        LoginMember loginMember = new LoginMember(MEMBER_ID, "USER", true);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                loginMember,
                null,
                List.of(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority(LoginMember.AUTHORITY_EMAIL_VERIFIED))));
    }

    private static RequestPostProcessor unverified() {
        LoginMember loginMember = new LoginMember(MEMBER_ID, "USER", false);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                loginMember, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
