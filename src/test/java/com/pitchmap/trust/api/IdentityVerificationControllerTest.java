package com.pitchmap.trust.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.GlobalExceptionHandler;
import com.pitchmap.common.security.LoginMember;
import com.pitchmap.common.security.SecurityConfig;
import com.pitchmap.common.trace.TraceIdFilter;
import com.pitchmap.trust.application.IdentityVerificationResult;
import com.pitchmap.trust.application.IdentityVerificationService;
import com.pitchmap.trust.application.IdentityVerifyCommand;
import com.pitchmap.trust.domain.Gender;
import com.pitchmap.trust.domain.TrustErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

@WebMvcTest(IdentityVerificationController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class})
class IdentityVerificationControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final String PATH = "/api/me/identity-verification";
    private static final String VALID_BODY =
            "{\"birthYear\":1995,\"gender\":\"FEMALE\",\"demoIdentityKey\":\"demo-1\"}";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private IdentityVerificationService identityVerificationService;

    @Test
    @DisplayName("[F-11] 인증 회원이 본인확인하면 200과 결과를 응답하고, 요청 값과 로그인한 회원의 ID를 서비스에 넘긴다")
    void verifiedMemberGetsResult() {
        // given
        when(identityVerificationService.verify(eq(MEMBER_ID), any()))
                .thenReturn(new IdentityVerificationResult(true, true, 1));

        // when
        MvcTestResult result = post(verified(), VALID_BODY);

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                { "identityVerified": true, "adult": true, "trustLevel": 1 }
                """);
        ArgumentCaptor<IdentityVerifyCommand> captor = ArgumentCaptor.forClass(IdentityVerifyCommand.class);
        verify(identityVerificationService).verify(eq(MEMBER_ID), captor.capture());
        assertThat(captor.getValue()).isEqualTo(new IdentityVerifyCommand(1995, Gender.FEMALE, "demo-1"));
    }

    @Test
    @DisplayName("[TR-03] 이메일 인증 전인 회원은 403 MEMBER_NOT_VERIFIED이고 서비스를 부르지 않는다")
    void unverifiedMemberIsForbidden() {
        MvcTestResult result = post(unverified(), VALID_BODY);

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        verifyNoInteractions(identityVerificationService);
    }

    @Test
    @DisplayName("로그인하지 않으면 401 AUTHENTICATION_REQUIRED이고 서비스를 부르지 않는다")
    void anonymousIsUnauthorized() {
        MvcTestResult result = mvc.post()
                .uri(PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
        verifyNoInteractions(identityVerificationService);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"gender\":\"FEMALE\",\"demoIdentityKey\":\"demo-1\"}",
                "{\"birthYear\":1995,\"demoIdentityKey\":\"demo-1\"}",
                "{\"birthYear\":1995,\"gender\":\"FEMALE\"}",
                "{\"birthYear\":1995,\"gender\":\"FEMALE\",\"demoIdentityKey\":\"  \"}",
                "{\"birthYear\":1995,\"gender\":\"OTHER\",\"demoIdentityKey\":\"demo-1\"}",
                "{\"birthYear\":\"abc\",\"gender\":\"FEMALE\",\"demoIdentityKey\":\"demo-1\"}"
            })
    @DisplayName("[F-11] 필드가 빠졌거나 허용 값 밖이면 400 INVALID_INPUT이고 서비스를 부르지 않는다")
    void invalidBodyIsBadRequest(String body) {
        MvcTestResult result = post(verified(), body);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        verifyNoInteractions(identityVerificationService);
    }

    @Test
    @DisplayName("시연용 식별 문자열이 100자를 넘으면 400 INVALID_INPUT이다")
    void tooLongKeyIsBadRequest() {
        String body =
                "{\"birthYear\":1995,\"gender\":\"FEMALE\",\"demoIdentityKey\":\"%s\"}".formatted("k".repeat(101));

        MvcTestResult result = post(verified(), body);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
    }

    @Test
    @DisplayName("[ID-04] 서비스가 던진 IDENTITY_ALREADY_VERIFIED와 IDENTITY_CI_DUPLICATED를 409로 응답한다")
    void conflictsAreMappedTo409() {
        when(identityVerificationService.verify(eq(MEMBER_ID), any()))
                .thenThrow(new BusinessException(TrustErrorCode.IDENTITY_ALREADY_VERIFIED))
                .thenThrow(new BusinessException(TrustErrorCode.IDENTITY_CI_DUPLICATED));

        MvcTestResult already = post(verified(), VALID_BODY);
        MvcTestResult duplicated = post(verified(), VALID_BODY);

        assertThat(already).hasStatus(HttpStatus.CONFLICT);
        assertThat(already).bodyJson().extractingPath("$.code").isEqualTo("IDENTITY_ALREADY_VERIFIED");
        assertThat(duplicated).hasStatus(HttpStatus.CONFLICT);
        assertThat(duplicated).bodyJson().extractingPath("$.code").isEqualTo("IDENTITY_CI_DUPLICATED");
    }

    private MvcTestResult post(RequestPostProcessor login, String body) {
        return mvc.post()
                .uri(PATH)
                .with(login)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

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
