package com.pitchmap.program.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import com.pitchmap.program.application.ProgramApplicationCancelResult;
import com.pitchmap.program.application.ProgramPayResult;
import com.pitchmap.program.application.ProgramPaymentService;
import com.pitchmap.program.domain.ProgramErrorCode;
import com.pitchmap.program.domain.ProgramException;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ProgramApplicationController.class)
@Import({
    GlobalExceptionHandler.class,
    TraceIdFilter.class,
    SecurityConfig.class,
    IdempotencyExecutor.class,
    ProgramApplicationControllerTest.ClockConfig.class
})
class ProgramApplicationControllerTest {

    private static final Instant AT = Instant.parse("2026-11-01T01:00:00Z");
    private static final String VALID_BODY = "{\"method\":\"FAKE_CARD\"}";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ProgramPaymentService programPaymentService;

    @MockitoBean
    private IdempotencyRecordJpaRepository idempotencyRecordRepository;

    @Test
    @DisplayName("[F-19] 인증 회원이 결제하면 200과 신청 ID, 상태, 결제 시각을 응답하고 로그인한 회원 ID로 서비스를 부른다")
    void payReturnsOk() {
        when(idempotencyRecordRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(programPaymentService.pay(4L, 70L)).thenReturn(new ProgramPayResult(70L, "CONFIRMED", AT));

        MvcTestResult result =
                payRequest(70L, "key-1", VALID_BODY).with(member(4L)).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.applicationId").isEqualTo(70);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("CONFIRMED");
        assertThat(result).bodyJson().extractingPath("$.paidAt").isEqualTo("2026-11-01T01:00:00Z");
        verify(programPaymentService).pay(4L, 70L);
    }

    @Test
    @DisplayName("[F-19][NFR-03] Idempotency-Key 헤더가 없으면 400 IDEMPOTENCY_KEY_REQUIRED이고 서비스를 부르지 않는다")
    void payRequiresIdempotencyKey() {
        MvcTestResult result = mvc.post()
                .uri("/api/program-applications/70/pay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .with(csrf())
                .with(member(4L))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
        verifyNoInteractions(programPaymentService);
    }

    @Test
    @DisplayName("[F-19] method가 없거나 모르는 값이거나 본문이 없으면 400 INVALID_INPUT이고 서비스를 부르지 않는다")
    void payRejectsInvalidMethod() {
        for (String body : new String[] {"{}", "{\"method\":\"BANK\"}", "{\"method\":null}"}) {
            MvcTestResult result =
                    payRequest(70L, "key-1", body).with(member(4L)).exchange();

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
        MvcTestResult noBody = mvc.post()
                .uri("/api/program-applications/70/pay")
                .header("Idempotency-Key", "key-1")
                .with(csrf())
                .with(member(4L))
                .exchange();
        assertThat(noBody).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(programPaymentService);
    }

    @Test
    @DisplayName("[F-19] 로그인하지 않고 결제하거나 취소하면 401 AUTHENTICATION_REQUIRED이다")
    void requiresLogin() {
        MvcTestResult pay = payRequest(70L, "key-1", VALID_BODY).exchange();
        MvcTestResult cancel = mvc.post()
                .uri("/api/program-applications/70/cancel")
                .with(csrf())
                .exchange();

        assertThat(pay).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(pay).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(cancel).hasStatus(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(programPaymentService);
    }

    @Test
    @DisplayName("[F-19][TR-03] 이메일 인증 전의 회원이 결제하거나 취소하면 403 MEMBER_NOT_VERIFIED이다")
    void rejectsUnverifiedMember() {
        LoginMember unverified = new LoginMember(4L, "USER", false);
        RequestPostProcessor principal = authentication(UsernamePasswordAuthenticationToken.authenticated(
                unverified, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        MvcTestResult pay = payRequest(70L, "key-1", VALID_BODY).with(principal).exchange();
        MvcTestResult cancel = mvc.post()
                .uri("/api/program-applications/70/cancel")
                .with(csrf())
                .with(principal)
                .exchange();

        assertThat(pay).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(pay).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        assertThat(cancel).hasStatus(HttpStatus.FORBIDDEN);
        verifyNoInteractions(programPaymentService);
    }

    @Test
    @DisplayName("[F-19] 서비스가 던진 오류 코드를 상태 코드와 함께 응답한다")
    void payMapsServiceErrors() {
        when(idempotencyRecordRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(programPaymentService.pay(4L, 1L)).thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));
        when(programPaymentService.pay(4L, 2L)).thenThrow(new BusinessException(CommonErrorCode.ACCESS_DENIED));
        when(programPaymentService.pay(4L, 3L))
                .thenThrow(new ProgramException(ProgramErrorCode.PROGRAM_PAYMENT_EXPIRED));
        when(programPaymentService.pay(4L, 4L)).thenThrow(new ProgramException(ProgramErrorCode.PROGRAM_INVALID_STATE));

        assertPayError(1L, HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertPayError(2L, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
        assertPayError(3L, HttpStatus.CONFLICT, "PROGRAM_PAYMENT_EXPIRED");
        assertPayError(4L, HttpStatus.CONFLICT, "PROGRAM_INVALID_STATE");
    }

    @Test
    @DisplayName("[F-19][PG-06] 인증 회원이 취소하면 200과 상태, 환불 여부를 응답하고 멱등성 키 없이도 된다")
    void cancelReturnsOk() {
        when(programPaymentService.cancel(4L, 70L)).thenReturn(new ProgramApplicationCancelResult("CANCELED", true));

        MvcTestResult result = mvc.post()
                .uri("/api/program-applications/70/cancel")
                .with(csrf())
                .with(member(4L))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("CANCELED");
        assertThat(result).bodyJson().extractingPath("$.refunded").isEqualTo(true);
    }

    @Test
    @DisplayName("[F-19][PG-06] 취소 오류 코드를 상태 코드와 함께 응답한다")
    void cancelMapsServiceErrors() {
        when(programPaymentService.cancel(4L, 1L)).thenThrow(new BusinessException(CommonErrorCode.ACCESS_DENIED));
        when(programPaymentService.cancel(4L, 2L))
                .thenThrow(new ProgramException(ProgramErrorCode.PROGRAM_CANCEL_NOT_ALLOWED));

        MvcTestResult denied = mvc.post()
                .uri("/api/program-applications/1/cancel")
                .with(csrf())
                .with(member(4L))
                .exchange();
        MvcTestResult tooLate = mvc.post()
                .uri("/api/program-applications/2/cancel")
                .with(csrf())
                .with(member(4L))
                .exchange();

        assertThat(denied).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(denied).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        assertThat(tooLate).hasStatus(HttpStatus.CONFLICT);
        assertThat(tooLate).bodyJson().extractingPath("$.code").isEqualTo("PROGRAM_CANCEL_NOT_ALLOWED");
    }

    @Test
    @DisplayName("[F-19] CSRF 토큰 없이 결제하면 403이다")
    void payRequiresCsrf() {
        MvcTestResult result = mvc.post()
                .uri("/api/program-applications/70/pay")
                .header("Idempotency-Key", "key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .with(member(4L))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        verifyNoInteractions(programPaymentService);
    }

    private void assertPayError(long applicationId, HttpStatus status, String code) {
        MvcTestResult result = payRequest(applicationId, "key-" + applicationId, VALID_BODY)
                .with(member(4L))
                .exchange();

        assertThat(result).hasStatus(status);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
    }

    private MockMvcTester.MockMvcRequestBuilder payRequest(long applicationId, String key, String body) {
        return mvc.post()
                .uri("/api/program-applications/" + applicationId + "/pay")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(csrf());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfig {

        @Bean
        Clock clock() {
            return MutableClock.atDefaultInstant();
        }
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
