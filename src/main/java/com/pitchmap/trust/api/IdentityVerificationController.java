package com.pitchmap.trust.api;

import com.pitchmap.common.security.LoginMember;
import com.pitchmap.trust.application.IdentityVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class IdentityVerificationController {

    private final IdentityVerificationService identityVerificationService;

    IdentityVerificationController(IdentityVerificationService identityVerificationService) {
        this.identityVerificationService = identityVerificationService;
    }

    @Operation(
            summary = "본인확인",
            description = "이메일 인증을 마친 회원이 출생연도, 성별, 시연용 식별 문자열(demoIdentityKey)로 본인확인한다. "
                    + "실제 본인확인기관이 아니라 가짜 제공자가 입력값을 그대로 믿는다. 같은 demoIdentityKey는 같은 사람으로 본다. "
                    + "서버는 출생연도·성별과 CI 해시만 저장하고 CI 원값은 저장하지 않는다. "
                    + "200과 함께 identityVerified, adult, trustLevel을 준다. 만 19세가 되는 해의 1월 1일(한국 시각)부터 성인이고, "
                    + "성인이 아니면 adult는 false, trustLevel은 0이다. "
                    + "출생연도가 1900년보다 이르거나 올해보다 늦거나, gender가 MALE·FEMALE이 아니거나, "
                    + "demoIdentityKey가 비었거나 100자를 넘으면 400 INVALID_INPUT으로 응답한다. "
                    + "이미 본인확인한 회원이면 409 IDENTITY_ALREADY_VERIFIED로, "
                    + "같은 demoIdentityKey로 본인확인한 다른 계정이 있으면 409 IDENTITY_CI_DUPLICATED로 응답한다.")
    @PostMapping("/api/me/identity-verification")
    IdentityVerificationResponse verify(
            @AuthenticationPrincipal LoginMember loginMember, @Valid @RequestBody IdentityVerificationRequest request) {
        return IdentityVerificationResponse.from(
                identityVerificationService.verify(loginMember.memberId(), request.toCommand()));
    }
}
