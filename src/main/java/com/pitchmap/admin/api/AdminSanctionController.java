package com.pitchmap.admin.api;

import com.pitchmap.common.security.LoginMember;
import com.pitchmap.trust.application.SanctionLiftService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/admin/** 경로는 보안 설정이 ADMIN 역할에만 연다.
@RestController
@RequestMapping("/api/admin/sanctions")
class AdminSanctionController {

    private final SanctionLiftService sanctionLiftService;

    AdminSanctionController(SanctionLiftService sanctionLiftService) {
        this.sanctionLiftService = sanctionLiftService;
    }

    @Operation(
            summary = "제재 해제",
            description = "적용 중(ACTIVE)인 제재를 해제(LIFTED)한다. 경고와 확정 정지 모두 해제할 수 있고, 정지를 해제하면 회원의 정지 상태를 "
                    + "남은 정지에 맞춰 다시 정한다. 해제한 제재는 다음 제재 단계 계산에서 빠진다. 이미 정리한 베이스캠프와 결제 대기 신청은 되돌리지 않는다. "
                    + "제재가 없으면 404 NOT_FOUND, 적용 중이 아니면 409 SANCTION_INVALID_STATE이다.")
    @PostMapping("/{id}/lift")
    SanctionLiftResponse lift(@PathVariable long id, @AuthenticationPrincipal LoginMember admin) {
        return SanctionLiftResponse.from(sanctionLiftService.lift(id, admin.memberId()));
    }
}
