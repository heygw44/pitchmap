package com.pitchmap.program.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.error.GlobalExceptionHandler;
import com.pitchmap.common.security.LoginMember;
import com.pitchmap.common.security.SecurityConfig;
import com.pitchmap.common.trace.TraceIdFilter;
import com.pitchmap.program.application.ProgramDetail;
import com.pitchmap.program.application.ProgramListQuery;
import com.pitchmap.program.application.ProgramPage;
import com.pitchmap.program.application.ProgramQueryService;
import com.pitchmap.program.application.ProgramSummary;
import com.pitchmap.program.domain.ProgramPhase;
import java.time.Instant;
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

@WebMvcTest(ProgramController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class})
class ProgramControllerTest {

    private static final Instant AT = Instant.parse("2026-11-01T01:00:00Z");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ProgramQueryService programQueryService;

    @Test
    @DisplayName("[F-17] 로그인하지 않아도 행사 목록을 조회하고, status와 page를 서비스에 넘긴다")
    void anonymousCanListPrograms() {
        ProgramSummary summary =
                new ProgramSummary(9L, "가을 백패킹", "설악산 입구", null, AT, AT, AT, AT, 200, 150, 30000, true, "OPEN");
        when(programQueryService.list(any(ProgramListQuery.class)))
                .thenReturn(new ProgramPage(List.of(summary), 1, 5, true));

        MvcTestResult result = mvc.get()
                .uri("/api/programs")
                .param("status", "OPEN")
                .param("page", "1")
                .param("size", "5")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.content[0].programId").isEqualTo(9);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].remainingSeats")
                .isEqualTo(150);
        assertThat(result).bodyJson().extractingPath("$.content[0].spotId").isNull();
        assertThat(result).bodyJson().extractingPath("$.content[0].status").isEqualTo("OPEN");
        assertThat(result).bodyJson().extractingPath("$.hasNext").isEqualTo(true);
        verify(programQueryService).list(new ProgramListQuery(ProgramPhase.OPEN, 1, 5));
    }

    @Test
    @DisplayName("[F-17] 목록의 status가 UPCOMING, OPEN, CLOSED가 아니거나 size가 1~50을 벗어나면 400 INVALID_INPUT이다")
    void listRejectsInvalidParameters() {
        for (String[] param :
                new String[][] {{"status", "CANCELED"}, {"status", "open"}, {"size", "51"}, {"page", "-1"}}) {
            MvcTestResult result =
                    mvc.get().uri("/api/programs").param(param[0], param[1]).exchange();

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
    }

    @Test
    @DisplayName("[F-17] 비로그인 요청자의 상세 응답에는 myApplication 필드가 없다")
    void anonymousDetailOmitsMyApplication() {
        when(programQueryService.detail(9L, null)).thenReturn(detail(null));

        MvcTestResult result = mvc.get().uri("/api/programs/9").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.programId").isEqualTo(9);
        assertThat(result).bodyJson().extractingPath("$.description").isEqualTo("설명");
        assertThat(result).bodyJson().doesNotHavePath("$.myApplication");
    }

    @Test
    @DisplayName("[F-17] 신청이 있는 회원의 상세 응답에는 myApplication이 있고, 로그인한 회원 ID로 서비스를 부른다")
    void memberDetailIncludesMyApplication() {
        when(programQueryService.detail(eq(9L), eq(4L)))
                .thenReturn(detail(new ProgramDetail.MyApplication(70L, "PENDING_PAYMENT", AT)));

        MvcTestResult result = mvc.get().uri("/api/programs/9").with(member(4L)).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.myApplication.applicationId")
                .isEqualTo(70);
        assertThat(result).bodyJson().extractingPath("$.myApplication.status").isEqualTo("PENDING_PAYMENT");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.myApplication.paymentDueAt")
                .isEqualTo("2026-11-01T01:00:00Z");
    }

    @Test
    @DisplayName("[F-17] 없는 행사의 상세는 404 NOT_FOUND이다")
    void missingProgramIsNotFound() {
        when(programQueryService.detail(404L, null)).thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));

        MvcTestResult result = mvc.get().uri("/api/programs/404").exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
    }

    private static ProgramDetail detail(ProgramDetail.MyApplication mine) {
        return new ProgramDetail(
                9L, "가을 백패킹", "설명", "설악산 입구", null, AT, AT, AT, AT, 200, 150, 30000, 15, true, "OPEN", mine);
    }

    private static RequestPostProcessor member(long memberId) {
        LoginMember loginMember = new LoginMember(memberId, "USER", true);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                loginMember,
                null,
                List.of(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority(LoginMember.AUTHORITY_EMAIL_VERIFIED))));
    }
}
