package com.pitchmap.program.api;

import com.pitchmap.common.idempotency.IdempotencyExecutor;
import com.pitchmap.common.idempotency.IdempotentRequest;
import com.pitchmap.common.security.LoginMember;
import com.pitchmap.program.application.ProgramPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class ProgramApplicationController {

    private final ProgramPaymentService programPaymentService;
    private final IdempotencyExecutor idempotencyExecutor;

    @Operation(
            summary = "행사 신청 가짜 결제",
            description =
                    "로그인한 인증 회원이 자기 결제 대기 신청을 결제해 확정한다. 실제 결제 기관을 부르지 않는다. "
                            + "Idempotency-Key 헤더가 필요하다. 없으면 400 IDEMPOTENCY_KEY_REQUIRED, 영문·숫자·'-'·'_' 1~64자가 아니면 400 INVALID_INPUT이다. "
                            + "같은 키로 다시 보내면 처음 결과를 그대로 돌려주고, 처음 요청이 아직 처리 중이면 409 IDEMPOTENCY_IN_PROGRESS이다. "
                            + "본문의 method는 FAKE_CARD만 받고, 없거나 모르는 값이면 400 INVALID_INPUT이다. "
                            + "성공하면 200과 applicationId, status(CONFIRMED), paidAt(결제 시각)을 돌려준다. 결제 금액은 결제하는 시점의 행사 참가비이다. "
                            + "오류는 이 순서로 처음 걸린 하나만 응답한다. 신청이 없으면 404 NOT_FOUND, 남의 신청이면 403 ACCESS_DENIED, "
                            + "결제 기한이 지났거나(기한 정각 포함) 만료된 신청이면 409 PROGRAM_PAYMENT_EXPIRED, 이미 확정됐거나 취소된 신청이면 409 PROGRAM_INVALID_STATE이다.")
    @PostMapping("/api/program-applications/{applicationId}/pay")
    ProgramPayResponse pay(
            @PathVariable long applicationId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ProgramPayRequest request,
            @AuthenticationPrincipal LoginMember loginMember) {
        long memberId = loginMember.memberId();
        return idempotencyExecutor.execute(
                new IdempotentRequest(
                        memberId, idempotencyKey, "POST /api/program-applications/" + applicationId + "/pay", request),
                HttpStatus.OK,
                ProgramPayResponse.class,
                () -> ProgramPayResponse.from(programPaymentService.pay(memberId, applicationId)));
    }

    @Operation(
            summary = "행사 신청 취소",
            description =
                    "로그인한 인증 회원이 자기 신청을 취소한다. Idempotency-Key 헤더는 쓰지 않는다. "
                            + "결제 대기 신청은 결제 기한이 지났어도 아직 만료 처리 전이면 취소할 수 있다. "
                            + "확정 신청은 행사 시작 72시간 전 정각까지만 취소할 수 있고, 취소하면 결제를 환불한다. "
                            + "성공하면 200과 status(CANCELED), refunded(환불했는지 여부)를 돌려준다. 같은 요청을 다시 보내면 409 PROGRAM_INVALID_STATE이다. "
                            + "오류는 이 순서로 처음 걸린 하나만 응답한다. 신청이 없으면 404 NOT_FOUND, 남의 신청이면 403 ACCESS_DENIED, "
                            + "이미 취소되거나 만료된 신청이면 409 PROGRAM_INVALID_STATE, 확정 신청인데 환불 기한이 지났으면 409 PROGRAM_CANCEL_NOT_ALLOWED이다.")
    @PostMapping("/api/program-applications/{applicationId}/cancel")
    ProgramApplicationCancelResponse cancel(
            @PathVariable long applicationId, @AuthenticationPrincipal LoginMember loginMember) {
        return ProgramApplicationCancelResponse.from(
                programPaymentService.cancel(loginMember.memberId(), applicationId));
    }
}
