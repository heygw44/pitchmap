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
 * 이 클래스가 같은 모양의 본문을 직접 쓴다. 로그인하지 않았으면 401이다. 이메일 인증을 마치지 않은 회원이 인증이 필요한 경로를 부르면
 * {@code MEMBER_NOT_VERIFIED}, 그 밖에 권한이 없거나 CSRF 토큰이 틀리면 {@code ACCESS_DENIED}로 응답하고 둘 다 403이다.
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
        // CSRF 실패(CsrfException)는 이메일 인증 규칙의 거부 결과가 아니므로 이 분기를 건너뛰고 ACCESS_DENIED가 된다.
        if (EmailVerifiedAuthorizationManager.isEmailVerificationRequired(accessDeniedException)) {
            write(response, CommonErrorCode.MEMBER_NOT_VERIFIED);
            return;
        }
        write(response, CommonErrorCode.ACCESS_DENIED);
    }

    private static void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getOutputStream().write(JSON_MAPPER.writeValueAsBytes(ErrorResponse.of(errorCode)));
    }
}
