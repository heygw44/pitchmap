package com.pitchmap.admin.api;

import com.pitchmap.common.security.LoginMember;
import com.pitchmap.program.application.ProgramAdminService;
import com.pitchmap.program.application.ProgramApplicantQuery;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// /api/admin/** 경로는 보안 설정이 ADMIN 역할에만 연다.
@RestController
@RequestMapping("/api/admin/programs")
class AdminProgramController {

    private static final int MAX_PAGE_SIZE = 50;

    private final ProgramAdminService programAdminService;

    AdminProgramController(ProgramAdminService programAdminService) {
        this.programAdminService = programAdminService;
    }

    @Operation(
            summary = "공식 행사 등록",
            description = "관리자가 행사를 등록하고 201과 함께 programId를 준다. 제목 1~100자, 설명 1~5000자, 장소 설명 1~255자, 정원 1~1000, "
                    + "참가비 0~1,000,000원, 결제 기한 1~1440분(생략하면 15분)이다. spotId는 선택이고 지도에 보이는 장소여야 한다. "
                    + "시각은 신청 시작 < 신청 마감 <= 행사 시작 < 행사 종료 순서여야 하고, 행사 시작은 현재보다 늦어야 한다. "
                    + "필수 값이 없거나 범위·시각 순서가 틀리거나 장소를 연결할 수 없으면 400 INVALID_INPUT이다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AdminProgramCreateResponse create(
            @Valid @RequestBody AdminProgramCreateRequest request, @AuthenticationPrincipal LoginMember admin) {
        return new AdminProgramCreateResponse(programAdminService.create(admin.memberId(), request.toCommand()));
    }

    @Operation(
            summary = "공식 행사 수정",
            description =
                    "보낸 필드만 고치고 공개 상세와 같은 모양(myApplication 없음)으로 응답한다. 서버는 고친 뒤의 값으로 범위와 시각 순서를 다시 검사한다. "
                            + "spotId를 null로 보내면 지도 장소와의 연결을 끊는다. 고칠 필드가 하나도 없거나 값이 틀리면 400 INVALID_INPUT, 행사가 없으면 404 NOT_FOUND이다. "
                            + "취소된 행사는 409 PROGRAM_INVALID_STATE, 신청 시작 시각이 지난 행사의 정원을 줄이면 409 PROGRAM_CAPACITY_DECREASE이다(늘리기는 허용한다).")
    @PatchMapping("/{programId}")
    AdminProgramDetailResponse revise(
            @PathVariable long programId,
            @Valid @RequestBody AdminProgramReviseRequest request,
            @AuthenticationPrincipal LoginMember admin) {
        return AdminProgramDetailResponse.from(
                programAdminService.revise(admin.memberId(), programId, request.toCommand()));
    }

    @Operation(
            summary = "공식 행사 취소",
            description = "행사를 CANCELED로 바꾸고, 결제 대기·확정 신청을 모두 취소 사유 PROGRAM_CANCELED로 취소하며, 결제 완료 건을 환불 처리한다. "
                    + "신청자에게 알림은 보내지 않는다. 응답은 programId, status(CANCELED), 함께 취소된 신청 수 canceledApplicationCount이다. "
                    + "행사가 없으면 404 NOT_FOUND, 이미 취소된 행사이면 409 PROGRAM_INVALID_STATE이다.")
    @PostMapping("/{programId}/cancel")
    AdminProgramCancelResponse cancel(@PathVariable long programId, @AuthenticationPrincipal LoginMember admin) {
        return AdminProgramCancelResponse.from(programAdminService.cancel(admin.memberId(), programId));
    }

    @Operation(
            summary = "행사 신청자 목록",
            description =
                    "행사의 신청을 신청한 순서(applicationId 오름차순)로 돌려준다. status(PENDING_PAYMENT, CONFIRMED, CANCELED, EXPIRED)를 보내면 그 상태만 준다. "
                            + "항목마다 applicationId, member(memberId, nickname), status, paymentDueAt, confirmedAt, canceledAt, cancelReason, createdAt이 있고 이메일은 없다. "
                            + "page는 0부터 세고 기본값은 0이다. size는 1~50이고 기본값은 20이다. "
                            + "행사가 없으면 404 NOT_FOUND, status가 허용 값이 아니거나 page·size가 범위를 벗어나면 400 INVALID_INPUT이다.")
    @GetMapping("/{programId}/applications")
    AdminProgramApplicantPageResponse applications(
            @PathVariable long programId,
            @RequestParam(required = false)
                    @Pattern(
                            regexp = "PENDING_PAYMENT|CONFIRMED|CANCELED|EXPIRED",
                            message = "status는 PENDING_PAYMENT, CONFIRMED, CANCELED, EXPIRED 중 하나여야 합니다.")
                    String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return AdminProgramApplicantPageResponse.from(
                programAdminService.applications(programId, new ProgramApplicantQuery(status, page, size)));
    }
}
