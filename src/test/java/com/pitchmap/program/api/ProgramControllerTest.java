package com.pitchmap.program.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.error.GlobalExceptionHandler;
import com.pitchmap.common.idempotency.IdempotencyExecutor;
import com.pitchmap.common.idempotency.IdempotencyRecordJpaRepository;
import com.pitchmap.common.security.LoginMember;
import com.pitchmap.common.security.SecurityConfig;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.trace.TraceIdFilter;
import com.pitchmap.program.application.ProgramApplyResult;
import com.pitchmap.program.application.ProgramApplyService;
import com.pitchmap.program.application.ProgramDetail;
import com.pitchmap.program.application.ProgramListQuery;
import com.pitchmap.program.application.ProgramPage;
import com.pitchmap.program.application.ProgramQueryService;
import com.pitchmap.program.application.ProgramSummary;
import com.pitchmap.program.application.ProgramVacancyAlertService;
import com.pitchmap.program.domain.ProgramErrorCode;
import com.pitchmap.program.domain.ProgramException;
import com.pitchmap.program.domain.ProgramPhase;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ProgramController.class)
@Import({
    GlobalExceptionHandler.class,
    TraceIdFilter.class,
    SecurityConfig.class,
    IdempotencyExecutor.class,
    ProgramControllerTest.ClockConfig.class
})
class ProgramControllerTest {

    private static final Instant AT = Instant.parse("2026-11-01T01:00:00Z");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ProgramQueryService programQueryService;

    @MockitoBean
    private ProgramApplyService programApplyService;

    @MockitoBean
    private ProgramVacancyAlertService programVacancyAlertService;

    @MockitoBean
    private IdempotencyRecordJpaRepository idempotencyRecordRepository;

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

    @Test
    @DisplayName("[F-18] 인증 회원이 신청하면 201과 신청 ID, 상태, 결제 기한, 금액을 응답한다")
    void applyReturnsCreated() {
        when(idempotencyRecordRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(programApplyService.apply(4L, 9L)).thenReturn(new ProgramApplyResult(70L, "PENDING_PAYMENT", AT, 30000));

        MvcTestResult result = applyRequest(9L, "key-1").with(member(4L)).exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.applicationId").isEqualTo(70);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("PENDING_PAYMENT");
        assertThat(result).bodyJson().extractingPath("$.paymentDueAt").isEqualTo("2026-11-01T01:00:00Z");
        assertThat(result).bodyJson().extractingPath("$.amount").isEqualTo(30000);
    }

    @Test
    @DisplayName("[F-18][NFR-03] Idempotency-Key 헤더가 없으면 400 IDEMPOTENCY_KEY_REQUIRED이고 서비스를 부르지 않는다")
    void applyRequiresIdempotencyKey() {
        MvcTestResult result = mvc.post()
                .uri("/api/programs/9/applications")
                .with(csrf())
                .with(member(4L))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
        verifyNoInteractions(programApplyService);
    }

    @Test
    @DisplayName("[F-18] 로그인하지 않고 신청하면 401 AUTHENTICATION_REQUIRED이다")
    void applyRequiresLogin() {
        MvcTestResult result = mvc.post()
                .uri("/api/programs/9/applications")
                .header("Idempotency-Key", "key-1")
                .with(csrf())
                .exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    @DisplayName("[F-18][TR-03] 이메일 인증 전의 회원이 신청하면 403 MEMBER_NOT_VERIFIED이다")
    void applyRejectsUnverifiedMember() {
        LoginMember unverified = new LoginMember(4L, "USER", false);
        RequestPostProcessor principal = authentication(UsernamePasswordAuthenticationToken.authenticated(
                unverified, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        MvcTestResult result = applyRequest(9L, "key-1").with(principal).exchange();

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        verifyNoInteractions(programApplyService);
    }

    @Test
    @DisplayName("[F-18] CSRF 토큰 없이 신청하면 403이다")
    void applyRequiresCsrf() {
        MvcTestResult result = mvc.post()
                .uri("/api/programs/9/applications")
                .header("Idempotency-Key", "key-1")
                .with(member(4L))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        verifyNoInteractions(programApplyService);
    }

    @Test
    @DisplayName("[F-18] 서비스가 던진 오류 코드를 상태 코드와 함께 응답한다")
    void applyMapsServiceErrors() {
        when(idempotencyRecordRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(programApplyService.apply(4L, 1L)).thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));
        when(programApplyService.apply(4L, 2L))
                .thenThrow(new ProgramException(ProgramErrorCode.PROGRAM_NOT_IN_APPLY_PERIOD));
        when(programApplyService.apply(4L, 3L)).thenThrow(new ProgramException(ProgramErrorCode.PROGRAM_SOLD_OUT));
        when(programApplyService.apply(4L, 4L))
                .thenThrow(new BusinessException(CommonErrorCode.TRUST_LEVEL_INSUFFICIENT));

        assertError(1L, HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertError(2L, HttpStatus.BAD_REQUEST, "PROGRAM_NOT_IN_APPLY_PERIOD");
        assertError(3L, HttpStatus.CONFLICT, "PROGRAM_SOLD_OUT");
        assertError(4L, HttpStatus.FORBIDDEN, "TRUST_LEVEL_INSUFFICIENT");
    }

    @Test
    @DisplayName("[F-20][PG-07] 빈자리 알림을 새로 신청하면 201, 이미 신청한 상태이면 200이고 본문은 없다")
    void subscribeVacancyAlertReturnsCreatedOrOk() {
        when(programVacancyAlertService.subscribe(4L, 9L)).thenReturn(true);
        when(programVacancyAlertService.subscribe(4L, 10L)).thenReturn(false);

        MvcTestResult created = vacancyAlertPost(9L).with(member(4L)).exchange();
        MvcTestResult existing = vacancyAlertPost(10L).with(member(4L)).exchange();

        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(created).body().isEmpty();
        assertThat(existing).hasStatus(HttpStatus.OK);
        assertThat(existing).body().isEmpty();
    }

    @Test
    @DisplayName("[F-20][PG-07] 빈자리 알림을 해제하면 204이다")
    void unsubscribeVacancyAlertReturnsNoContent() {
        MvcTestResult result = mvc.delete()
                .uri("/api/programs/9/vacancy-alerts")
                .with(csrf())
                .with(member(4L))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        verify(programVacancyAlertService).unsubscribe(4L, 9L);
    }

    @Test
    @DisplayName("[F-20][PG-07] 로그인하지 않고 빈자리 알림을 신청하거나 해제하면 401 AUTHENTICATION_REQUIRED이다")
    void vacancyAlertRequiresLogin() {
        MvcTestResult subscribe = vacancyAlertPost(9L).exchange();
        MvcTestResult unsubscribe =
                mvc.delete().uri("/api/programs/9/vacancy-alerts").with(csrf()).exchange();

        assertThat(subscribe).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(subscribe).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(unsubscribe).hasStatus(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(programVacancyAlertService);
    }

    @Test
    @DisplayName("[F-20][PG-07] 서비스가 던진 NOT_FOUND와 PROGRAM_INVALID_STATE를 404와 409로 응답한다")
    void subscribeVacancyAlertMapsServiceErrors() {
        when(programVacancyAlertService.subscribe(4L, 1L)).thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));
        when(programVacancyAlertService.subscribe(4L, 2L))
                .thenThrow(new ProgramException(ProgramErrorCode.PROGRAM_INVALID_STATE));

        MvcTestResult notFound = vacancyAlertPost(1L).with(member(4L)).exchange();
        MvcTestResult invalidState = vacancyAlertPost(2L).with(member(4L)).exchange();

        assertThat(notFound).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(notFound).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(invalidState).hasStatus(HttpStatus.CONFLICT);
        assertThat(invalidState).bodyJson().extractingPath("$.code").isEqualTo("PROGRAM_INVALID_STATE");
    }

    private MockMvcTester.MockMvcRequestBuilder vacancyAlertPost(long programId) {
        return mvc.post().uri("/api/programs/" + programId + "/vacancy-alerts").with(csrf());
    }

    private void assertError(long programId, HttpStatus status, String code) {
        MvcTestResult result =
                applyRequest(programId, "key-" + programId).with(member(4L)).exchange();

        assertThat(result).hasStatus(status);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
    }

    private MockMvcTester.MockMvcRequestBuilder applyRequest(long programId, String key) {
        return mvc.post()
                .uri("/api/programs/" + programId + "/applications")
                .header("Idempotency-Key", key)
                .with(csrf());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfig {

        @Bean
        Clock clock() {
            return MutableClock.atDefaultInstant();
        }
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
