package com.pitchmap.common.security;

import java.util.function.Supplier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * 이메일 인증을 마친 회원({@link LoginMember#AUTHORITY_EMAIL_VERIFIED} 권한)만 통과시킨다.
 *
 * <p>권한이 없는 요청은 일반 거부 결과 대신 이 클래스만 쓰는 전용 거부 결과로 거부한다. 접근 거부 처리기는 이 값을 보고
 * 거부 사유가 이 규칙이라는 것을 알아내서 {@code MEMBER_NOT_VERIFIED}로 응답한다. 로그인했고 권한이 없다는 사실만 보고 판단하면,
 * 인증을 마치지 않은 회원이 관리자 경로처럼 다른 이유로 거부된 요청에도 "이메일 인증이 필요하다"는 잘못된 안내를 받는다.
 */
final class EmailVerifiedAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final AuthorizationManager<RequestAuthorizationContext> hasEmailVerifiedAuthority =
            AuthorityAuthorizationManager.hasAuthority(LoginMember.AUTHORITY_EMAIL_VERIFIED);

    @Override
    public AuthorizationResult authorize(
            Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        AuthorizationResult result = hasEmailVerifiedAuthority.authorize(authentication, context);
        if (result != null && result.isGranted()) {
            return result;
        }
        return DenialReason.EMAIL_VERIFICATION_REQUIRED;
    }

    static boolean isEmailVerificationRequired(AccessDeniedException exception) {
        return exception instanceof AuthorizationDeniedException denied
                && denied.getAuthorizationResult() == DenialReason.EMAIL_VERIFICATION_REQUIRED;
    }

    private enum DenialReason implements AuthorizationResult {
        EMAIL_VERIFICATION_REQUIRED;

        @Override
        public boolean isGranted() {
            return false;
        }
    }
}
