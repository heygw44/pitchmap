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
import com.pitchmap.trust.application.MemberReportCommand;
import com.pitchmap.trust.application.MemberReportService;
import com.pitchmap.trust.domain.ReportKind;
import com.pitchmap.trust.domain.ReportType;
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

@WebMvcTest(MemberReportController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class})
class MemberReportControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final String URI = "/api/member-reports";
    private static final String VALID_BODY =
            "{\"targetMemberId\":31,\"basecampId\":21,\"kind\":\"MEMBER\",\"type\":\"NO_SHOW\",\"content\":\"오지 않았다\"}";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private MemberReportService reportService;

    @Test
    @DisplayName("[F-16][SN-01] 신고하면 201과 reportId, RECEIVED를 응답하고, 요청 값과 로그인한 회원의 ID를 서비스에 넘긴다")
    void reportReturnsCreated() {
        when(reportService.report(eq(MEMBER_ID), any())).thenReturn(55L);

        MvcTestResult result = post(verified(), VALID_BODY);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().isStrictlyEqualTo("{ \"reportId\": 55, \"status\": \"RECEIVED\" }");
        ArgumentCaptor<MemberReportCommand> captor = ArgumentCaptor.forClass(MemberReportCommand.class);
        verify(reportService).report(eq(MEMBER_ID), captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(new MemberReportCommand(31L, 21L, ReportKind.MEMBER, null, ReportType.NO_SHOW, "오지 않았다"));
    }

    @Test
    @DisplayName("[SN-07] 후기 신고는 동행 후기 ID를 서비스에 넘긴다")
    void reviewReportPassesReviewId() {
        when(reportService.report(eq(MEMBER_ID), any())).thenReturn(1L);

        MvcTestResult result = post(
                verified(),
                "{\"targetMemberId\":31,\"basecampId\":21,\"kind\":\"REVIEW\",\"companionReviewId\":9,"
                        + "\"type\":\"INAPPROPRIATE_REVIEW\",\"content\":\"허위 후기\"}");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        ArgumentCaptor<MemberReportCommand> captor = ArgumentCaptor.forClass(MemberReportCommand.class);
        verify(reportService).report(eq(MEMBER_ID), captor.capture());
        assertThat(captor.getValue().companionReviewId()).isEqualTo(9L);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "{\"basecampId\":21,\"kind\":\"MEMBER\",\"type\":\"NO_SHOW\",\"content\":\"내용\"}",
                "{\"targetMemberId\":31,\"kind\":\"MEMBER\",\"type\":\"NO_SHOW\",\"content\":\"내용\"}",
                "{\"targetMemberId\":31,\"basecampId\":21,\"type\":\"NO_SHOW\",\"content\":\"내용\"}",
                "{\"targetMemberId\":31,\"basecampId\":21,\"kind\":\"MEMBER\",\"content\":\"내용\"}",
                "{\"targetMemberId\":31,\"basecampId\":21,\"kind\":\"MEMBER\",\"type\":\"NO_SHOW\"}",
                "{\"targetMemberId\":31,\"basecampId\":21,\"kind\":\"MEMBER\",\"type\":\"NO_SHOW\",\"content\":\" \"}",
                "{\"targetMemberId\":0,\"basecampId\":21,\"kind\":\"MEMBER\",\"type\":\"NO_SHOW\",\"content\":\"내용\"}",
                "{\"targetMemberId\":31,\"basecampId\":-1,\"kind\":\"MEMBER\",\"type\":\"NO_SHOW\",\"content\":\"내용\"}",
                "{\"targetMemberId\":31,\"basecampId\":21,\"kind\":\"REVIEW\",\"companionReviewId\":0,"
                        + "\"type\":\"INAPPROPRIATE_REVIEW\",\"content\":\"내용\"}",
                "{\"targetMemberId\":31,\"basecampId\":21,\"kind\":\"OTHER\",\"type\":\"NO_SHOW\",\"content\":\"내용\"}",
                "{\"targetMemberId\":31,\"basecampId\":21,\"kind\":\"MEMBER\",\"type\":\"UNKNOWN\",\"content\":\"내용\"}"
            })
    @DisplayName("[F-16] 필수 값이 없거나 양수가 아니거나 알 수 없는 종류·유형이면 400 INVALID_INPUT을 응답하고 서비스를 부르지 않는다")
    void reportRejectsInvalidBody(String body) {
        MvcTestResult result = post(verified(), body);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        verifyNoInteractions(reportService);
    }

    @Test
    @DisplayName("[F-16] 내용은 1000자까지 받고 1001자부터 400 INVALID_INPUT이다")
    void contentLengthBoundary() {
        when(reportService.report(eq(MEMBER_ID), any())).thenReturn(1L);

        MvcTestResult atLimit = post(verified(), bodyWithContent("가".repeat(1000)));
        MvcTestResult overLimit = post(verified(), bodyWithContent("가".repeat(1001)));

        assertThat(atLimit).hasStatus(HttpStatus.CREATED);
        assertThat(overLimit).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(overLimit).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
    }

    @Test
    @DisplayName("[F-16] 서비스가 던진 REPORT_NOT_ELIGIBLE은 403, REPORT_DUPLICATED는 409로 응답한다")
    void mapsDomainErrors() {
        when(reportService.report(eq(MEMBER_ID), any()))
                .thenThrow(new BusinessException(TrustErrorCode.REPORT_NOT_ELIGIBLE))
                .thenThrow(new BusinessException(TrustErrorCode.REPORT_DUPLICATED));

        MvcTestResult notEligible = post(verified(), VALID_BODY);
        MvcTestResult duplicated = post(verified(), VALID_BODY);

        assertThat(notEligible).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(notEligible).bodyJson().extractingPath("$.code").isEqualTo("REPORT_NOT_ELIGIBLE");
        assertThat(duplicated).hasStatus(HttpStatus.CONFLICT);
        assertThat(duplicated).bodyJson().extractingPath("$.code").isEqualTo("REPORT_DUPLICATED");
    }

    @Test
    @DisplayName("[TR-03] 이메일 인증 전 회원은 403 MEMBER_NOT_VERIFIED이고 로그인하지 않으면 401이다")
    void requiresVerifiedMember() {
        MvcTestResult unverified = post(unverified(), VALID_BODY);
        MvcTestResult anonymous = mvc.post()
                .uri(URI)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .exchange();

        assertThat(unverified).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(unverified).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        assertThat(anonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(reportService);
    }

    private MvcTestResult post(RequestPostProcessor login, String body) {
        return mvc.post()
                .uri(URI)
                .with(login)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static String bodyWithContent(String content) {
        return "{\"targetMemberId\":31,\"basecampId\":21,\"kind\":\"MEMBER\",\"type\":\"NO_SHOW\",\"content\":\""
                + content + "\"}";
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
