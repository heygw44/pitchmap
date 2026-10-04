package com.pitchmap.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.security.web.authentication.SpringSessionRememberMeServices;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration(proxyBeanMethods = false)
public class SessionCookieConfig {

    private static final String SESSION_COOKIE_NAME = "SESSION";

    // Spring Boot는 server.servlet.session.cookie.* 속성을 임베디드 서버로 실행할 때만 세션 쿠키에 반영한다.
    // 하지만 @SpringBootTest의 MOCK 환경처럼 임베디드 서버가 없으면 Boot는 WAR 배포로 보고 속성을 무시해서,
    // 쿠키에 HttpOnly, Secure, SameSite가 빠진다. 그래서 실행 환경과 상관없이 같은 쿠키가 나가도록 속성 대신 이 빈에 직접 적는다.
    // Secure 쿠키는 http://localhost에서도 브라우저가 받아 주지만, curl은 http로 다시 보내지 않으므로 손으로 시험할 때는 https를 쓴다.
    @Bean
    CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(SESSION_COOKIE_NAME);
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(true);
        serializer.setSameSite("Lax");
        // 이 서비스는 로그인 유지(remember-me)를 쓰지 않는다. 하지만 이 속성이 비어 있으면 Spring Session이 시작할 때 경고를 남긴다.
        serializer.setRememberMeRequestAttribute(SpringSessionRememberMeServices.REMEMBER_ME_LOGIN_ATTR);
        return serializer;
    }
}
