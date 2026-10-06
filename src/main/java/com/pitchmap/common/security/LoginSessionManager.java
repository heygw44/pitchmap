package com.pitchmap.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 검사를 끝낸 회원을 세션에 로그인시키고 로그아웃시킨다.
 *
 * <p>Spring Security의 폼 로그인 필터를 쓰지 않으므로, 로그인 API가 이 클래스를 직접 호출해 세션과 SecurityContext를 만든다.
 */
@Component
public class LoginSessionManager {

    private static final String ROLE_PREFIX = "ROLE_";

    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();
    private final SessionAuthenticationStrategy sessionFixationStrategy = new ChangeSessionIdAuthenticationStrategy();
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public LoginSessionManager(SecurityContextRepository securityContextRepository) {
        this.securityContextRepository = securityContextRepository;
    }

    public void login(LoginMember member, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = authenticationOf(member);

        // 로그인 전부터 쓰던 세션 ID를 공격자가 알고 있을 수 있다. 그래서 세션이 이미 있으면 로그인하기 전에 ID를 바꾼다.
        // 세션이 없으면 아래 saveContext가 새 세션을 만들므로 이전 ID가 남지 않는다.
        sessionFixationStrategy.onAuthentication(authentication, request, response);

        storeContext(authentication, request, response);
    }

    /**
     * 현재 세션의 회원이 이메일 인증을 마쳤다고 표시한다. 세션 ID는 바꾸지 않고 같은 세션에 인증 권한만 더한다.
     *
     * <p>이 호출은 현재 요청의 세션만 바꾼다. 같은 회원이 다른 기기에서 로그인해 둔 세션은 다음에 로그인할 때까지 예전 권한을 가진다.
     * 세션에 저장된 권한은 로그인할 때 정해지고, 이 메서드는 그 회원의 다른 세션을 찾아 고치지 않기 때문이다.
     */
    public void markEmailVerified(HttpServletRequest request, HttpServletResponse response) {
        Authentication current = securityContextHolderStrategy.getContext().getAuthentication();
        if (current == null || !(current.getPrincipal() instanceof LoginMember member)) {
            throw new IllegalStateException("로그인한 회원이 없는 요청에서 이메일 인증 표시를 요청했습니다.");
        }
        if (member.emailVerified()) {
            return;
        }
        LoginMember verified = new LoginMember(member.memberId(), member.role(), true);
        storeContext(authenticationOf(verified), request, response);
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        logoutHandler.logout(
                request, response, securityContextHolderStrategy.getContext().getAuthentication());
    }

    private static Authentication authenticationOf(LoginMember member) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + member.role()));
        if (member.emailVerified()) {
            authorities.add(new SimpleGrantedAuthority(LoginMember.AUTHORITY_EMAIL_VERIFIED));
        }
        return UsernamePasswordAuthenticationToken.authenticated(member, null, authorities);
    }

    private void storeContext(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
