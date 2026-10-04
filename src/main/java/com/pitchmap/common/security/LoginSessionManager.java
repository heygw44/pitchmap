package com.pitchmap.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
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
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                member, null, List.of(new SimpleGrantedAuthority(ROLE_PREFIX + member.role())));

        // 로그인 전부터 쓰던 세션 ID를 공격자가 알고 있을 수 있다. 그래서 세션이 이미 있으면 로그인하기 전에 ID를 바꾼다.
        // 세션이 없으면 아래 saveContext가 새 세션을 만들므로 이전 ID가 남지 않는다.
        sessionFixationStrategy.onAuthentication(authentication, request, response);

        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        logoutHandler.logout(
                request, response, securityContextHolderStrategy.getContext().getAuthentication());
    }
}
