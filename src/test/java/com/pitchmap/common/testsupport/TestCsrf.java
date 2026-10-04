package com.pitchmap.common.testsupport;

import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * MockMvc 요청에 CSRF 토큰을 실어 보내는 도우미다. 브라우저가 하듯이 {@code XSRF-TOKEN} 쿠키와 {@code X-XSRF-TOKEN} 헤더에
 * 같은 값을 넣는다.
 *
 * <p>spring-security-test의 {@code csrf()}를 쓰면 안 된다. 이 후처리기는 요청 하나가 아니라, 컨텍스트에 하나뿐인 {@code CsrfFilter}의
 * 토큰 저장소를 세션 방식으로 바꿔 놓고 되돌리지 않는다. 통합 테스트는 Spring 컨텍스트를 나눠 쓰므로, 한 테스트가 {@code csrf()}를 쓰면
 * 그 뒤에 실행되는 테스트에서는 서버가 쿠키를 내려주지 않는다. 그래서 테스트 클래스 실행 순서에 따라 CSRF 쿠키를 확인하는 테스트가 깨진다.
 * 이 도우미는 요청에 값을 싣기만 하므로 서버의 저장소를 바꾸지 않는다.
 */
public final class TestCsrf {

    private static final String COOKIE_NAME = "XSRF-TOKEN";
    private static final String HEADER_NAME = "X-XSRF-TOKEN";

    private TestCsrf() {}

    public static RequestPostProcessor csrf() {
        String token = UUID.randomUUID().toString();
        return request -> {
            request.setCookies(appendCookie(request.getCookies(), new Cookie(COOKIE_NAME, token)));
            request.addHeader(HEADER_NAME, token);
            return request;
        };
    }

    private static Cookie[] appendCookie(Cookie[] existing, Cookie added) {
        if (existing == null) {
            return new Cookie[] {added};
        }
        Cookie[] merged = Arrays.copyOf(existing, existing.length + 1);
        merged[existing.length] = added;
        return merged;
    }
}
