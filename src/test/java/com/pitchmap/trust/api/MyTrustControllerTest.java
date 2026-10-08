package com.pitchmap.trust.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.pitchmap.common.error.GlobalExceptionHandler;
import com.pitchmap.common.security.LoginMember;
import com.pitchmap.common.security.SecurityConfig;
import com.pitchmap.common.trace.TraceIdFilter;
import com.pitchmap.trust.application.TrustDetail;
import com.pitchmap.trust.application.TrustSummaryService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(MyTrustController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class})
class MyTrustControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final String PATH = "/api/me/trust";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private TrustSummaryService trustSummaryService;

    @Test
    @DisplayName("[TR-01] 단계 1이면 nextLevel에 단계 2 조건과 현재 값을 담고, 후기가 없으면 rejoinRate.current는 null이다")
    void levelOneHasNextLevel() {
        when(trustSummaryService.detail(MEMBER_ID))
                .thenReturn(new TrustDetail(true, true, 1, 2, null, 1, true, List.of(), "TWENTIES", "MALE"));

        MvcTestResult result = mvc.get().uri(PATH).with(login()).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {
                  "trustLevel": 1,
                  "identityVerified": true,
                  "nextLevel": {
                    "level": 2,
                    "completedCompanions": { "current": 2, "required": 3 },
                    "rejoinRate": { "current": null, "required": 80 },
                    "recentEarlyLeaves": { "current": 1, "limit": 3 },
                    "noRecentSanction": true
                  }
                }
                """);
    }

    @Test
    @DisplayName("[TR-01] 단계 2이면 nextLevel 필드를 응답에서 뺀다")
    void levelTwoOmitsNextLevel() {
        when(trustSummaryService.detail(MEMBER_ID))
                .thenReturn(new TrustDetail(true, true, 2, 3, 100, 0, true, List.of(), "TWENTIES", "MALE"));

        MvcTestResult result = mvc.get().uri(PATH).with(login()).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                { "trustLevel": 2, "identityVerified": true }
                """);
    }

    @Test
    @DisplayName("로그인하지 않으면 401 AUTHENTICATION_REQUIRED이고 서비스를 부르지 않는다")
    void anonymousIsUnauthorized() {
        MvcTestResult result = mvc.get().uri(PATH).exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
        verifyNoInteractions(trustSummaryService);
    }

    private static RequestPostProcessor login() {
        LoginMember loginMember = new LoginMember(MEMBER_ID, "USER", false);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                loginMember, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
