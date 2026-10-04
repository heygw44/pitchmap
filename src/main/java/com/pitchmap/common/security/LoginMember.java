package com.pitchmap.common.security;

import java.io.Serial;
import java.io.Serializable;
import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * 로그인한 회원이다. Spring Session JDBC가 이 객체를 JDK 직렬화로 세션 속성 테이블에 저장하므로 Serializable이어야 한다.
 *
 * <p>{@link #getName()}은 회원 ID 문자열이다. Spring Session은 이 이름으로 세션 테이블의 principal 이름 열을 채우고,
 * 비밀번호 재설정이나 제재처럼 한 회원의 세션을 모두 지워야 할 때 {@code findByPrincipalName}으로 그 세션을 찾는다.
 *
 * <p>{@code emailVerified}가 true이면 {@link LoginSessionManager}가 {@link #AUTHORITY_EMAIL_VERIFIED} 권한을 세션에 붙이고,
 * 이메일 인증이 필요한 경로는 이 권한으로 접근을 판단한다.
 *
 * <p>직렬화 형식을 바꿀 때는 {@code serialVersionUID}를 올리지 않는다. 이미 저장된 세션이 있으면 올리는 순간 역직렬화가 실패해서
 * 로그인한 모든 회원이 풀린다. 구성요소를 더하면 예전 세션에서는 기본값(false)으로 읽히고, 이는 인증 권한을 주지 않는 쪽이라 안전하다.
 */
public record LoginMember(long memberId, String role, boolean emailVerified)
        implements AuthenticatedPrincipal, Serializable {

    public static final String AUTHORITY_EMAIL_VERIFIED = "EMAIL_VERIFIED";

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String getName() {
        return String.valueOf(memberId);
    }
}
