package com.pitchmap.trust.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.pitchmap.common.error.GlobalExceptionHandler;
import com.pitchmap.common.security.LoginMember;
import com.pitchmap.common.security.LoginSessionManager;
import com.pitchmap.common.security.SecurityConfig;
import com.pitchmap.common.trace.TraceIdFilter;
import com.pitchmap.member.application.MyInfoService;
import com.pitchmap.trust.application.TrustSummaryService;
import com.pitchmap.trust.application.WithdrawalService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

@WebMvcTest(MeController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class})
class MeControllerWithdrawalTest {

    private static final long MEMBER_ID = 7L;
    private static final String PATH = "/api/me";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private MyInfoService myInfoService;

    @MockitoBean
    private TrustSummaryService trustSummaryService;

    @MockitoBean
    private WithdrawalService withdrawalService;

    @MockitoBean
    private LoginSessionManager loginSessionManager;

    @Test
    @DisplayName("[F-01][PV-01] 비밀번호를 보내면 204이고, 이메일 인증 전인 회원도 탈퇴 서비스와 로그아웃을 호출한다")
    void withdrawsWithPassword() {
        MvcTestResult result = delete("{\"password\":\"Valid-pass1\"}", login());

        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        verify(withdrawalService).withdraw(MEMBER_ID, "Valid-pass1");
        verify(loginSessionManager).logout(any(), any());
    }

    @Test
    @DisplayName("[F-01][PV-01] 비밀번호가 비어 있거나 없으면 400 INVALID_INPUT이고 서비스를 부르지 않는다")
    void blankPasswordIsInvalidInput() {
        for (String body : List.of("{\"password\":\"\"}", "{\"password\":\"   \"}", "{}")) {
            MvcTestResult result = delete(body, login());

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
        verifyNoInteractions(withdrawalService);
    }

    @Test
    @DisplayName("[F-01][PV-01] 로그인하지 않으면 401 AUTHENTICATION_REQUIRED이고 서비스를 부르지 않는다")
    void anonymousIsUnauthorized() {
        MvcTestResult result = mvc.delete()
                .uri(PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"Valid-pass1\"}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
        verifyNoInteractions(withdrawalService);
    }

    @Test
    @DisplayName("[F-01][PV-01] CSRF 토큰 없이 탈퇴를 요청하면 403이고 서비스를 부르지 않는다")
    void missingCsrfTokenIsForbidden() {
        MvcTestResult result = mvc.delete()
                .uri(PATH)
                .with(login())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"Valid-pass1\"}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        verifyNoInteractions(withdrawalService);
    }

    private MvcTestResult delete(String body, RequestPostProcessor user) {
        return mvc.delete()
                .uri(PATH)
                .with(csrf())
                .with(user)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static RequestPostProcessor login() {
        LoginMember loginMember = new LoginMember(MEMBER_ID, "USER", false);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                loginMember, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
