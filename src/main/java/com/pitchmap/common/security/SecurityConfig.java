package com.pitchmap.common.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderNotFoundException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfFilter;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    private static final String ADMIN_ROLE = "ADMIN";

    // 인증 없이 부를 수 있는 엔드포인트다. 경로의 {변수}는 한 구간을 뜻하는 *로 쓴다.
    private static final String[] PUBLIC_GET_PATTERNS = {
        "/api/members/*/profile",
        "/api/spots",
        "/api/spots/nearby",
        "/api/spots/*",
        "/api/spots/*/reviews",
        "/api/basecamps",
        "/api/basecamps/*",
        "/api/programs",
        "/api/programs/*",
    };

    private static final String[] PUBLIC_POST_PATTERNS = {
        "/api/members", "/api/auth/login", "/api/auth/password-reset/request", "/api/auth/password-reset/confirm",
    };

    private static final String[] INFRASTRUCTURE_PATTERNS = {"/actuator/health", "/swagger-ui/**", "/v3/api-docs/**"};

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository securityContextRepository)
            throws Exception {
        SecurityErrorResponseHandler errorResponseHandler = new SecurityErrorResponseHandler();
        http.authorizeHttpRequests(SecurityConfig::authorizeRequests)
                // 쿠키 기반 세션이라 CSRF 방어를 끌 수 없다. SPA 설정은 XSRF-TOKEN 쿠키를 JS가 읽게 열고 X-XSRF-TOKEN 헤더를 검사한다.
                .csrf(csrf -> csrf.spa())
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                // 로그인과 로그아웃은 LoginSessionManager가 처리하고, 인증 요청에 쓸 기본 화면과 인증 방식은 쓰지 않는다.
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                // JSON API라 거부된 요청을 세션에 저장했다가 로그인 뒤에 다시 보낼 이유가 없다.
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorResponseHandler)
                        .accessDeniedHandler(errorResponseHandler));
        return http.build();
    }

    private static void authorizeRequests(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry requests) {
        requests.dispatcherTypeMatchers(DispatcherType.ERROR)
                .permitAll()
                // 관리자 규칙을 먼저 둬서, 나중에 공개 경로를 추가하다 실수로 관리자 경로를 열지 못하게 한다.
                .requestMatchers("/api/admin/**")
                .hasRole(ADMIN_ROLE)
                .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATTERNS)
                .permitAll()
                .requestMatchers(HttpMethod.POST, PUBLIC_POST_PATTERNS)
                .permitAll()
                .requestMatchers(INFRASTRUCTURE_PATTERNS)
                .permitAll()
                // 인증 회원, 본인확인, 캠프 리더 같은 세부 자격은 각 서비스가 회원 ID로 검사한다. 여기서는 로그인 여부만 본다.
                .requestMatchers("/api/**")
                .authenticated()
                .anyRequest()
                .denyAll();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    // 비밀번호 확인은 회원 모듈의 로그인 서비스가 하고, Spring Security의 인증 매니저는 쓰지 않는다.
    // 하지만 인증 매니저나 사용자 조회 빈이 하나도 없으면 Spring Boot가 임시 비밀번호를 가진 사용자를 만들고 로그에 비밀번호를 남긴다.
    // 이 빈을 두면 Boot가 그 사용자를 만들지 않는다. 이 매니저는 어떤 인증 요청도 받지 않는다.
    @Bean
    AuthenticationManager authenticationManager() {
        return authentication -> {
            throw new ProviderNotFoundException("이 서버는 Spring Security 인증 매니저로 인증하지 않는다.");
        };
    }
}
