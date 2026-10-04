package com.pitchmap.member.api;

import com.pitchmap.common.security.LoginMember;
import com.pitchmap.common.security.LoginSessionManager;
import com.pitchmap.member.application.LoginResult;
import com.pitchmap.member.application.MemberLoginService;
import com.pitchmap.member.domain.MemberStatus;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final MemberLoginService memberLoginService;
    private final LoginSessionManager loginSessionManager;

    AuthController(MemberLoginService memberLoginService, LoginSessionManager loginSessionManager) {
        this.memberLoginService = memberLoginService;
        this.loginSessionManager = loginSessionManager;
    }

    @Operation(summary = "로그인")
    @PostMapping("/login")
    LoginResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        LoginResult result = memberLoginService.login(request.toCommand(httpRequest.getRemoteAddr()));
        // 인증 코드를 확인하기 전(UNVERIFIED)에도 로그인할 수 있다. 다만 이메일 인증이 필요한 경로는 ACTIVE 회원만 통과한다.
        boolean emailVerified = result.status() == MemberStatus.ACTIVE;
        loginSessionManager.login(
                new LoginMember(result.memberId(), result.role().name(), emailVerified), httpRequest, httpResponse);
        return LoginResponse.from(result);
    }

    @Operation(summary = "로그아웃")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        loginSessionManager.logout(httpRequest, httpResponse);
    }
}
