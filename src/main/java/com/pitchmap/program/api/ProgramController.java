package com.pitchmap.program.api;

import com.pitchmap.common.idempotency.IdempotencyExecutor;
import com.pitchmap.common.idempotency.IdempotentRequest;
import com.pitchmap.common.security.LoginMember;
import com.pitchmap.program.application.ProgramApplyService;
import com.pitchmap.program.application.ProgramQueryService;
import com.pitchmap.program.application.ProgramVacancyAlertService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class ProgramController {

    private final ProgramQueryService programQueryService;
    private final ProgramApplyService programApplyService;
    private final ProgramVacancyAlertService programVacancyAlertService;
    private final IdempotencyExecutor idempotencyExecutor;

    @Operation(
            summary = "공식 행사 목록",
            description = "취소되지 않은 행사를 행사 시작 시각이 이른 순서로 돌려준다(시작 시각이 같으면 programId 순서). 로그인하지 않아도 조회할 수 있다. "
                    + "status를 보내면 UPCOMING(신청 시작 전), OPEN(신청 시작 시각 이상, 마감 시각 미만), CLOSED(마감 시각 이후) 중 그 단계의 행사만 준다. "
                    + "항목마다 programId, title, locationText, spotId(지도 장소와 연결하지 않았으면 null), startAt, endAt, applyOpenAt, applyCloseAt, "
                    + "capacity, remainingSeats(정원에서 결제 대기·확정 신청 수를 뺀 값, 0 미만이면 0), fee, overnight, status가 있다. "
                    + "page는 0부터 시작하고 기본값은 0이다. size는 1~50이고 기본값은 20이다. 응답에는 전체 개수가 없고 다음 페이지가 있는지만 hasNext로 알려 준다. "
                    + "status가 허용 값이 아니거나 page·size가 범위를 벗어나면 400 INVALID_INPUT이다.")
    @GetMapping("/api/programs")
    ProgramPageResponse list(@Valid @ParameterObject @ModelAttribute ProgramListRequest request) {
        return ProgramPageResponse.from(programQueryService.list(request.toQuery()));
    }

    @Operation(
            summary = "공식 행사 상세",
            description = "행사 하나의 정보를 돌려준다. 로그인하지 않아도 조회할 수 있다. 목록 항목에 description과 paymentDeadlineMinutes가 더해진다. "
                    + "로그인한 회원에게 이 행사의 신청이 있으면 가장 최근 신청을 myApplication(applicationId, status, paymentDueAt)으로 담고, "
                    + "비로그인이거나 신청이 없으면 이 필드를 뺀다. "
                    + "취소된 행사도 status가 CANCELED인 상세로 돌려준다. 행사가 없으면 404 NOT_FOUND이다.")
    @GetMapping("/api/programs/{programId}")
    ProgramDetailResponse detail(@PathVariable long programId, @AuthenticationPrincipal LoginMember loginMember) {
        Long viewerId = loginMember == null ? null : loginMember.memberId();
        return ProgramDetailResponse.from(programQueryService.detail(programId, viewerId));
    }

    @Operation(
            summary = "행사 선착순 신청",
            description = "로그인한 인증 회원이 행사에 신청한다. Idempotency-Key 헤더가 필요하다. 없으면 400 IDEMPOTENCY_KEY_REQUIRED, "
                    + "영문·숫자·'-'·'_' 1~64자가 아니면 400 INVALID_INPUT이다. 같은 키로 다시 보내면 처음 결과를 그대로 돌려주고, "
                    + "처음 요청이 아직 처리 중이면 409 IDEMPOTENCY_IN_PROGRESS이다. "
                    + "성공하면 201과 applicationId, status(PENDING_PAYMENT), paymentDueAt(결제 기한), amount(결제할 금액)를 돌려준다. "
                    + "오류는 이 순서로 처음 걸린 하나만 응답한다. 행사가 없으면 404 NOT_FOUND, 취소된 행사이면 409 PROGRAM_INVALID_STATE, "
                    + "신청 시작 전이거나 마감 뒤이면 400 PROGRAM_NOT_IN_APPLY_PERIOD, 숙박 행사인데 신뢰 단계가 1 미만이면 403 TRUST_LEVEL_INSUFFICIENT, "
                    + "이미 결제 대기·확정 신청이 있으면 409 PROGRAM_ALREADY_APPLIED, 남은 자리가 없으면 409 PROGRAM_SOLD_OUT이다. "
                    + "취소하거나 만료된 신청이 있는 회원은 다시 신청할 수 있다.")
    @PostMapping("/api/programs/{programId}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    ProgramApplyResponse apply(
            @PathVariable long programId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal LoginMember loginMember) {
        long memberId = loginMember.memberId();
        return idempotencyExecutor.execute(
                new IdempotentRequest(
                        memberId, idempotencyKey, "POST /api/programs/" + programId + "/applications", null),
                HttpStatus.CREATED,
                ProgramApplyResponse.class,
                () -> ProgramApplyResponse.from(programApplyService.apply(memberId, programId)));
    }

    @Operation(
            summary = "행사 빈자리 알림 신청",
            description = "로그인한 인증 회원이 행사의 빈자리 알림을 신청한다. 새로 신청하면 201, 이미 신청한 상태이면 200이고 본문은 없다. "
                    + "남은 자리가 있어도 신청을 저장한다. 신청은 회원이 해제할 때까지 남고, 자리가 돌아올 때마다 알림을 보낸다. "
                    + "행사가 없으면 404 NOT_FOUND, 취소된 행사이거나 신청 마감 시각 이후이면 409 PROGRAM_INVALID_STATE이다.")
    @PostMapping("/api/programs/{programId}/vacancy-alerts")
    ResponseEntity<Void> subscribeVacancyAlert(
            @PathVariable long programId, @AuthenticationPrincipal LoginMember loginMember) {
        boolean created = programVacancyAlertService.subscribe(loginMember.memberId(), programId);
        return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK)
                .build();
    }

    @Operation(
            summary = "행사 빈자리 알림 해제",
            description =
                    "로그인한 인증 회원이 행사의 빈자리 알림 신청을 해제한다. 신청한 적이 없어도, 행사가 취소·마감됐어도 204이다. " + "행사가 없으면 404 NOT_FOUND이다.")
    @DeleteMapping("/api/programs/{programId}/vacancy-alerts")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unsubscribeVacancyAlert(@PathVariable long programId, @AuthenticationPrincipal LoginMember loginMember) {
        programVacancyAlertService.unsubscribe(loginMember.memberId(), programId);
    }
}
