package com.pitchmap.member.api;

import com.pitchmap.member.application.PasswordResetConfirmService;
import com.pitchmap.member.application.PasswordResetRequestService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/password-reset")
class PasswordResetController {

    private final PasswordResetRequestService passwordResetRequestService;
    private final PasswordResetConfirmService passwordResetConfirmService;

    PasswordResetController(
            PasswordResetRequestService passwordResetRequestService,
            PasswordResetConfirmService passwordResetConfirmService) {
        this.passwordResetRequestService = passwordResetRequestService;
        this.passwordResetConfirmService = passwordResetConfirmService;
    }

    @Operation(
            summary = "비밀번호 재설정 요청",
            description = "이메일로 재설정 링크를 보낸다. 가입 여부를 숨기려고 가입된 이메일이든 아니든 항상 204로 응답한다. 링크는 30분 안에 한 번만 쓸 수 있다.")
    @PostMapping("/request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void request(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetRequestService.request(request.email());
    }

    @Operation(
            summary = "비밀번호 재설정 확인",
            description = "메일로 받은 토큰으로 새 비밀번호를 정한다. 성공하면 그 회원의 모든 세션이 삭제된다. "
                    + "토큰이 만료됐거나 이미 사용됐으면 400 PASSWORD_RESET_TOKEN_INVALID, 비밀번호가 규칙을 어기면 "
                    + "400 MEMBER_PASSWORD_POLICY이고 이때 토큰은 그대로 쓸 수 있다.")
    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirm(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetConfirmService.confirm(request.token(), request.newPassword());
    }
}
