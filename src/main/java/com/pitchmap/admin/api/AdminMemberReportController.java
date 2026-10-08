package com.pitchmap.admin.api;

import com.pitchmap.admin.api.AdminMemberReportRequests.ActionRequest;
import com.pitchmap.admin.api.AdminMemberReportRequests.DismissRequest;
import com.pitchmap.common.security.LoginMember;
import com.pitchmap.trust.application.AdminMemberReportQueryService;
import com.pitchmap.trust.application.MemberReportActionCommand;
import com.pitchmap.trust.application.MemberReportActionService;
import com.pitchmap.trust.application.MemberReportDismissService;
import com.pitchmap.trust.application.MemberReportReviewService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// /api/admin/** 경로는 보안 설정이 ADMIN 역할에만 연다.
@RestController
@RequestMapping("/api/admin/member-reports")
class AdminMemberReportController {

    private static final int MAX_PAGE_SIZE = 50;

    private final AdminMemberReportQueryService queryService;
    private final MemberReportReviewService reviewService;
    private final MemberReportActionService actionService;
    private final MemberReportDismissService dismissService;

    AdminMemberReportController(
            AdminMemberReportQueryService queryService,
            MemberReportReviewService reviewService,
            MemberReportActionService actionService,
            MemberReportDismissService dismissService) {
        this.queryService = queryService;
        this.reviewService = reviewService;
        this.actionService = actionService;
        this.dismissService = dismissService;
    }

    @Operation(
            summary = "신고 목록",
            description = "긴급 신고, 금전 요구 신고, 나머지 순으로, 같은 묶음 안에서는 접수 시각이 이른 신고부터 돌려준다. "
                    + "status(RECEIVED, IN_REVIEW, ACTIONED, DISMISSED)와 urgent는 선택이고, 알 수 없는 status는 400 INVALID_INPUT이다. "
                    + "page는 0부터 세고, size는 기본 20, 1~50이다.")
    @GetMapping
    AdminMemberReportPageResponse list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean urgent,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return AdminMemberReportPageResponse.from(queryService.list(status, urgent, page, size));
    }

    @Operation(
            summary = "신고 상세",
            description = "신고 내용, 관련 베이스캠프, 후기 신고이면 신고된 후기, 신고 대상 회원의 제재 이력을 돌려준다. "
                    + "기각된 신고가 만든 임시 정지는 제재 이력에서 뺀다. 신고가 없으면 404 NOT_FOUND이다.")
    @GetMapping("/{id}")
    AdminMemberReportDetailResponse detail(@PathVariable long id) {
        return AdminMemberReportDetailResponse.from(queryService.detail(id));
    }

    @Operation(
            summary = "신고 검토 시작",
            description = "접수(RECEIVED) 상태의 신고를 검토 중(IN_REVIEW)으로 바꾼다. 신고가 없으면 404 NOT_FOUND, "
                    + "접수 상태가 아니면 409 REPORT_INVALID_STATE이다.")
    @PostMapping("/{id}/start-review")
    MemberReportStatusResponse startReview(@PathVariable long id, @AuthenticationPrincipal LoginMember admin) {
        return MemberReportStatusResponse.from(reviewService.startReview(id, admin.memberId()));
    }

    @Operation(
            summary = "신고 조치",
            description = "검토 중인 신고를 조치 완료(ACTIONED)로 바꾼다. sanction이 있으면 신고 대상 회원에게 제재를 확정하고, "
                    + "정지이면 그 회원의 세션을 모두 지운다. hideReview는 후기 신고에서만 true로 보낼 수 있고 신고된 동행 후기를 숨긴다. "
                    + "sanction이 null이고 hideReview가 false이면 400 INVALID_INPUT이다. "
                    + "서버는 요청 형식(400), 신고 없음(404 NOT_FOUND), 검토 중이 아님(409 REPORT_INVALID_STATE), "
                    + "신고 종류와 맞지 않는 요청(400), 제재 단계 불일치(400) 순서로 검사한다. 긴급 신고의 임시 정지는 그대로 두고 자연 만료되게 한다.")
    @PostMapping("/{id}/action")
    MemberReportActionResponse action(
            @PathVariable long id,
            @AuthenticationPrincipal LoginMember admin,
            @Valid @RequestBody ActionRequest request) {
        var sanction = request.sanction();
        return MemberReportActionResponse.from(actionService.act(new MemberReportActionCommand(
                id,
                admin.memberId(),
                sanction == null ? null : sanction.type(),
                sanction == null ? null : sanction.reason(),
                request.shouldHideReview(),
                request.note())));
    }

    @Operation(
            summary = "신고 기각",
            description = "검토 중인 신고를 기각(DISMISSED)한다. 그 신고가 만든 임시 정지가 적용 중이면 해제하고 회원의 정지 상태를 "
                    + "남은 정지에 맞춰 다시 정한다. 본문은 생략할 수 있다. 신고가 없으면 404 NOT_FOUND, 검토 중이 아니면 409 REPORT_INVALID_STATE이다.")
    @PostMapping("/{id}/dismiss")
    MemberReportStatusResponse dismiss(
            @PathVariable long id,
            @AuthenticationPrincipal LoginMember admin,
            @Valid @RequestBody(required = false) DismissRequest request) {
        String note = request == null ? null : request.note();
        return MemberReportStatusResponse.from(dismissService.dismiss(id, admin.memberId(), note));
    }
}
