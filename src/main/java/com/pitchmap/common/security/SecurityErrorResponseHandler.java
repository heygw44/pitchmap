package com.pitchmap.common.security;

import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.error.ErrorCode;
import com.pitchmap.common.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * 보안 필터가 거부한 요청에 {@link ErrorResponse} JSON으로 응답한다.
 *
 * <p>이 거부는 컨트롤러에 닿기 전에 필터에서 일어난다. 그래서 {@code GlobalExceptionHandler}가 처리하지 못하고,
 * 이 클래스가 같은 모양의 본문을 직접 쓴다. 로그인하지 않았으면 401, 권한이 없거나 CSRF 토큰이 틀리면 403이다.
 */
final class SecurityErrorResponseHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    // 필터는 Spring MVC의 메시지 변환기를 거치지 않으므로 직접 JSON으로 바꾼다. ErrorResponse는 설정이 필요 없는 단순한 record다.
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        write(response, CommonErrorCode.AUTHENTICATION_REQUIRED);
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        write(response, CommonErrorCode.ACCESS_DENIED);
    }

    private static void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getOutputStream().write(JSON_MAPPER.writeValueAsBytes(ErrorResponse.of(errorCode)));
    }
}
