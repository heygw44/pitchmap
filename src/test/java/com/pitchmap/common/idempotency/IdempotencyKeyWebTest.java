package com.pitchmap.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.pitchmap.common.error.GlobalExceptionHandler;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.trace.TraceIdFilter;
import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = IdempotencyKeyWebTest.TestController.class)
@Import({
    GlobalExceptionHandler.class,
    TraceIdFilter.class,
    IdempotencyExecutor.class,
    IdempotencyKeyWebTest.TestController.class,
    IdempotencyKeyWebTest.TestConfig.class
})
class IdempotencyKeyWebTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private IdempotencyRecordJpaRepository repository;

    @Test
    @DisplayName("[NFR-03] Idempotency-Key 헤더가 없으면 400과 IDEMPOTENCY_KEY_REQUIRED로 응답한다")
    void missingHeaderIsBadRequest() {
        MvcTestResult result = mvc.post().uri("/test/idempotent").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
    }

    @Test
    @DisplayName("[NFR-03] 형식이 틀린 키는 400과 INVALID_INPUT으로 응답한다")
    void malformedHeaderIsBadRequest() {
        MvcTestResult result = mvc.post()
                .uri("/test/idempotent")
                .header("Idempotency-Key", "bad key!")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
    }

    @Test
    @DisplayName("[NFR-03] 올바른 키면 업무 처리 결과를 성공 상태로 응답한다")
    void validHeaderRunsAction() {
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MvcTestResult result = mvc.post()
                .uri("/test/idempotent")
                .header("Idempotency-Key", "key-1")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyText().isEqualTo("done");
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class TestConfig {

        @Bean
        SecurityFilterChain permitAllFilterChain(HttpSecurity http) throws Exception {
            http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                    .csrf(AbstractHttpConfigurer::disable);
            return http.build();
        }

        @Bean
        Clock clock() {
            return MutableClock.atDefaultInstant();
        }
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        private final IdempotencyExecutor executor;

        TestController(IdempotencyExecutor executor) {
            this.executor = executor;
        }

        @PostMapping("/idempotent")
        @ResponseStatus(HttpStatus.CREATED)
        String idempotent(@RequestHeader(value = "Idempotency-Key", required = false) String key) {
            return executor.execute(
                    new IdempotentRequest(1L, key, "POST /test/idempotent", null),
                    HttpStatus.CREATED,
                    String.class,
                    () -> "done");
        }
    }
}
