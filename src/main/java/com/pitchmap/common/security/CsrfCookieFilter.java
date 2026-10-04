package com.pitchmap.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 첫 GET 응답에도 {@code XSRF-TOKEN} 쿠키를 내려준다.
 *
 * <p>Spring Security는 CSRF 토큰을 누가 읽을 때까지 만들지 않는다(지연 로딩). 그래서 화면 렌더링 없이 JSON만 주고받는
 * 이 API에서는 아무도 토큰을 읽지 않아, 프론트엔드가 쿠키를 받지 못하고 첫 POST가 403으로 실패한다.
 * 이 필터가 요청마다 토큰 값을 한 번 읽어 쿠키 저장소가 쿠키를 쓰게 만든다.
 */
final class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
