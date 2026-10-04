package com.pitchmap.common.security;

import java.io.Serial;
import java.io.Serializable;
import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * 로그인한 회원이다. Spring Session JDBC가 이 객체를 JDK 직렬화로 세션 속성 테이블에 저장하므로 Serializable이어야 한다.
 *
 * <p>{@link #getName()}은 회원 ID 문자열이다. Spring Session은 이 이름으로 세션 테이블의 principal 이름 열을 채우고,
 * 비밀번호 재설정이나 제재처럼 한 회원의 세션을 모두 지워야 할 때 {@code findByPrincipalName}으로 그 세션을 찾는다.
 */
public record LoginMember(long memberId, String role) implements AuthenticatedPrincipal, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String getName() {
        return String.valueOf(memberId);
    }
}
