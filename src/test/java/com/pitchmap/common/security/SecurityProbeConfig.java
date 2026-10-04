package com.pitchmap.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * {@link SecurityProbeController}가 쓰는 경로만 열어 두는 테스트 전용 보안 필터 체인이다.
 *
 * <p>이 클래스는 {@code @TestConfiguration}이 아니라 일반 {@code @Configuration}이다. 그래서 통합 테스트가 모두 컴포넌트 스캔으로 읽고,
 * 테스트마다 따로 import해서 Spring 컨텍스트 캐시 키가 달라지는 일이 없다. 운영 코드가 아니라 {@code src/test}에만 있으므로 운영에는 올라가지 않는다.
 */
@Configuration(proxyBeanMethods = false)
class SecurityProbeConfig {

    // 운영 필터 체인(SecurityConfig)은 모든 요청에 맞으므로, 이 체인이 먼저 평가되도록 가장 높은 우선순위를 준다.
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain securityProbeFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(SecurityProbeController.BASE_PATH + "/**")
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
