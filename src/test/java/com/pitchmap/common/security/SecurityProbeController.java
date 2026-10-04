package com.pitchmap.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인 API(회원 모듈)와 상관없이 {@link LoginSessionManager}와 세션 쿠키를 시험하려고 만든 테스트 전용 컨트롤러다.
 * 이 컨트롤러의 경로는 {@link SecurityProbeConfig}가 인증 없이 열어 둔다.
 */
@RestController
@RequestMapping(SecurityProbeController.BASE_PATH)
class SecurityProbeController {

    static final String BASE_PATH = "/security-test";

    private final LoginSessionManager loginSessionManager;

    SecurityProbeController(LoginSessionManager loginSessionManager) {
        this.loginSessionManager = loginSessionManager;
    }

    // 로그인하기 전에 세션만 만든다. 세션 고정 방지를 시험할 때 "로그인 전 세션"으로 쓴다.
    @PostMapping("/session")
    ResponseEntity<Void> createSession(HttpServletRequest request) {
        request.getSession(true);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    ResponseEntity<Void> login(
            @RequestParam long memberId,
            @RequestParam String role,
            HttpServletRequest request,
            HttpServletResponse response) {
        loginSessionManager.login(new LoginMember(memberId, role), request, response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        loginSessionManager.logout(request, response);
        return ResponseEntity.noContent().build();
    }
}
