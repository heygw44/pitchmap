package com.pitchmap.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.trace.TraceIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@Import({
    GlobalExceptionHandler.class,
    TraceIdFilter.class,
    GlobalExceptionHandlerTest.TestController.class,
    GlobalExceptionHandlerTest.PermitAllSecurityConfig.class
})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("BusinessException은 오류 코드의 상태와 코드, 메시지로 응답한다")
    void businessExceptionUsesErrorCodeStatus() {
        MvcTestResult result = mvc.get().uri("/test/business").exchange();

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        assertThat(result).bodyJson().extractingPath("$.message").isEqualTo(CommonErrorCode.ACCESS_DENIED.message());
        assertThat(result).bodyJson().doesNotHavePath("$.fieldErrors");
    }

    @Test
    @DisplayName("도메인 예외는 자신의 오류 코드로 응답한다")
    void domainExceptionUsesOwnErrorCode() {
        MvcTestResult result = mvc.get().uri("/test/domain").exchange();

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("SAMPLE_CONFLICT");
        assertThat(result).bodyJson().extractingPath("$.message").isEqualTo("샘플 충돌");
    }

    @Test
    @DisplayName("응답 본문의 traceId는 X-Trace-Id 헤더와 같다")
    void traceIdInBodyMatchesResponseHeader() {
        MvcTestResult result = mvc.get().uri("/test/business").exchange();

        String headerTraceId = result.getResponse().getHeader(TraceIdFilter.HEADER);
        assertThat(headerTraceId).isNotBlank();
        assertThat(result).bodyJson().extractingPath("$.traceId").isEqualTo(headerTraceId);
    }

    @Test
    @DisplayName("본문 검증에 실패하면 필드별 오류를 담아 400으로 응답한다")
    void invalidBodyReturnsFieldErrors() {
        MvcTestResult result = mvc.post()
                .uri("/test/body")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result).bodyJson().extractingPath("$.fieldErrors.length()").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.fieldErrors[0].field").isEqualTo("name");
        assertThat(result).bodyJson().extractingPath("$.fieldErrors[0].reason").isEqualTo("이름은 필수입니다.");
    }

    @Test
    @DisplayName("요청 파라미터 제약을 어기면 파라미터 이름으로 필드 오류를 담아 400으로 응답한다")
    void constraintViolatedRequestParamReturnsFieldError() {
        MvcTestResult result = mvc.get().uri("/test/size?size=0").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result).bodyJson().extractingPath("$.fieldErrors[0].field").isEqualTo("size");
    }

    @Test
    @DisplayName("파라미터 타입이 맞지 않으면 파라미터 이름을 담아 400으로 응답한다")
    void typeMismatchReturnsFieldError() {
        MvcTestResult result = mvc.get().uri("/test/size?size=abc").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result).bodyJson().extractingPath("$.fieldErrors[0].field").isEqualTo("size");
    }

    @Test
    @DisplayName("필수 파라미터가 없으면 파라미터 이름을 담아 400으로 응답한다")
    void missingRequestParamReturnsFieldError() {
        MvcTestResult result = mvc.get().uri("/test/size").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.fieldErrors[0].field").isEqualTo("size");
    }

    @Test
    @DisplayName("모델 속성의 값 형식이 틀리면 자바 타입 이름을 싣지 않고 필드 오류를 담아 400으로 응답한다")
    void modelAttributeBindingFailureDoesNotLeakInternalTypeNames() {
        MvcTestResult result = mvc.get().uri("/test/search?page=abc").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result).bodyJson().extractingPath("$.fieldErrors[0].field").isEqualTo("page");
        assertThat(result).body().asString().doesNotContain("java.");
    }

    @Test
    @DisplayName("JSON을 읽을 수 없으면 fieldErrors 없이 400으로 응답한다")
    void malformedJsonReturnsInvalidInput() {
        MvcTestResult result = mvc.post()
                .uri("/test/body")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result).bodyJson().doesNotHavePath("$.fieldErrors");
    }

    @Test
    @DisplayName("지원하지 않는 메서드는 Allow 헤더와 함께 405로 응답한다")
    void unsupportedMethodReturnsMethodNotAllowedWithAllowHeader() {
        MvcTestResult result = mvc.get().uri("/test/post-only").exchange();

        assertThat(result).hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(result.getResponse().getHeader("Allow")).contains("POST");
        assertThat(result).bodyJson().doesNotHavePath("$.fieldErrors");
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type은 415로 응답한다")
    void unsupportedContentTypeReturnsUnsupportedMediaType() {
        MvcTestResult result = mvc.post()
                .uri("/test/body")
                .contentType(MediaType.TEXT_PLAIN)
                .content("name")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    @DisplayName("없는 경로는 404로 응답한다")
    void unknownPathReturnsNotFound() {
        MvcTestResult result = mvc.get().uri("/test/unknown-path").exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(result).bodyJson().doesNotHavePath("$.fieldErrors");
    }

    @Test
    @DisplayName("예상하지 못한 예외는 원인 메시지 없이 500으로 응답한다")
    void unexpectedExceptionHidesCause() {
        MvcTestResult result = mvc.get().uri("/test/unexpected").exchange();

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INTERNAL_ERROR");
        assertThat(result).bodyText().doesNotContain("secret");
        assertThat(result).bodyJson().doesNotHavePath("$.fieldErrors");
    }

    @ParameterizedTest(name = "{0}ms -> {1}초")
    @CsvSource({"0,1", "1,1", "1000,1", "1001,2", "90000,90"})
    @DisplayName("[PW-03][EV-03] 요청 과다 예외는 429와 올림한 초 단위 Retry-After 헤더, 일반 오류 본문으로 응답한다")
    void rateLimitedExceptionAddsRetryAfterHeader(long retryAfterMillis, String expectedSeconds) {
        MvcTestResult result =
                mvc.get().uri("/test/rate-limited?millis=" + retryAfterMillis).exchange();

        assertThat(result).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(result.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo(expectedSeconds);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("TOO_MANY_REQUESTS");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.message")
                .isEqualTo(CommonErrorCode.TOO_MANY_REQUESTS.message());
        assertThat(result).bodyJson().extractingPath("$.traceId").asString().isNotBlank();
        assertThat(result).bodyJson().doesNotHavePath("$.fieldErrors");
    }

    @Test
    @DisplayName("요청 과다가 아닌 BusinessException에는 Retry-After 헤더를 붙이지 않는다")
    void businessExceptionHasNoRetryAfterHeader() {
        MvcTestResult result = mvc.get().uri("/test/business").exchange();

        assertThat(result.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNull();
    }

    @Test
    @DisplayName("예외가 extraFields를 알려 주면 응답 JSON 최상위에 덧붙이고 기본 필드는 그대로 둔다")
    void extraFieldsAreAppendedToTopLevelOfBody() {
        MvcTestResult result = mvc.get().uri("/test/extra").exchange();

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("SAMPLE_CONFLICT");
        assertThat(result).bodyJson().extractingPath("$.message").isEqualTo("샘플 충돌");
        assertThat(result).bodyJson().extractingPath("$.traceId").asString().isNotBlank();
        assertThat(result).bodyJson().extractingPath("$.until").isEqualTo("2026-10-05T03:00:00Z");
        assertThat(result).bodyJson().doesNotHavePath("$.extraFields");
        assertThat(result).bodyJson().doesNotHavePath("$.additionalFields");
    }

    @Test
    @DisplayName("extraFields가 없는 예외의 응답에는 기본 필드 외의 키가 없다")
    void bodyHasOnlyDefaultFieldsWithoutExtraFields() {
        MvcTestResult result = mvc.get().uri("/test/domain").exchange();

        assertThat(result)
                .bodyJson()
                .extractingPath("$")
                .asInstanceOf(InstanceOfAssertFactories.MAP)
                .containsOnlyKeys("code", "message", "traceId");
    }

    enum SampleErrorCode implements ErrorCode {
        SAMPLE_CONFLICT(HttpStatus.CONFLICT, "샘플 충돌");

        private final HttpStatus httpStatus;
        private final String message;

        SampleErrorCode(HttpStatus httpStatus, String message) {
            this.httpStatus = httpStatus;
            this.message = message;
        }

        @Override
        public HttpStatus httpStatus() {
            return httpStatus;
        }

        @Override
        public String message() {
            return message;
        }
    }

    static class SampleConflictException extends BusinessException {

        SampleConflictException() {
            super(SampleErrorCode.SAMPLE_CONFLICT);
        }
    }

    static class SampleDetailedException extends BusinessException {

        SampleDetailedException() {
            super(SampleErrorCode.SAMPLE_CONFLICT);
        }

        @Override
        public Map<String, Object> extraFields() {
            return Map.of("until", Instant.parse("2026-10-05T03:00:00Z"));
        }
    }

    // 이 테스트는 오류 응답 처리를 검사하는 것이 목적이다. 그래서 인증과 CSRF는 끄고 모든 요청을 통과시킨다.
    // 실제 보안 규칙은 SecurityConfigIntegrationTest가 검사한다.
    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class PermitAllSecurityConfig {

        @Bean
        SecurityFilterChain permitAllFilterChain(HttpSecurity http) throws Exception {
            http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                    .csrf(AbstractHttpConfigurer::disable);
            return http.build();
        }
    }

    record SampleRequest(@NotBlank(message = "이름은 필수입니다.") String name) {}

    record SampleSearch(Integer page) {}

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/business")
        String business() {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }

        @GetMapping("/domain")
        String domain() {
            throw new SampleConflictException();
        }

        @PostMapping("/body")
        String body(@Valid @RequestBody SampleRequest request) {
            return request.name();
        }

        @GetMapping("/size")
        String size(@RequestParam @Min(1) int size) {
            return String.valueOf(size);
        }

        @GetMapping("/search")
        String search(@Valid @ModelAttribute SampleSearch search) {
            return String.valueOf(search.page());
        }

        @GetMapping("/unexpected")
        String unexpected() {
            throw new IllegalStateException("secret");
        }

        @PostMapping("/post-only")
        String postOnly() {
            return "ok";
        }

        @GetMapping("/rate-limited")
        String rateLimited(@RequestParam long millis) {
            throw new RateLimitedException(CommonErrorCode.TOO_MANY_REQUESTS, Duration.ofMillis(millis));
        }

        @GetMapping("/extra")
        String extra() {
            throw new SampleDetailedException();
        }
    }
}
