package com.pitchmap.trust.api;

import com.pitchmap.common.security.LoginMember;
import com.pitchmap.trust.application.TrustSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MyTrustController {

    private final TrustSummaryService trustSummaryService;

    MyTrustController(TrustSummaryService trustSummaryService) {
        this.trustSummaryService = trustSummaryService;
    }

    @Operation(
            summary = "내 신뢰 단계 조회",
            description = "로그인한 회원의 신뢰 단계(0~2)와 본인확인 여부를 응답한다. 단계 2가 아니면 nextLevel에 단계 2 조건을 채운 정도를 담는다: "
                    + "완료한 동행 횟수, \"다시 동행\" 비율(공개된 후기만 세며 받은 후기가 없으면 current가 null), "
                    + "최근 180일 임박 탈퇴 횟수(current가 limit 이상이면 조건을 채우지 못함), 최근 180일 확정 제재가 없는지. "
                    + "단계 2이면 nextLevel 필드를 뺀다. 이메일 인증 전인 회원도 부를 수 있다.")
    @GetMapping("/api/me/trust")
    MyTrustResponse find(@AuthenticationPrincipal LoginMember loginMember) {
        return MyTrustResponse.from(trustSummaryService.detail(loginMember.memberId()));
    }
}
