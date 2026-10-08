package com.pitchmap.trust.api;

import com.pitchmap.common.security.LoginMember;
import com.pitchmap.trust.application.MemberReportService;
import com.pitchmap.trust.domain.ReportStatus;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MemberReportController {

    private final MemberReportService memberReportService;

    MemberReportController(MemberReportService memberReportService) {
        this.memberReportService = memberReportService;
    }

    @Operation(
            summary = "회원·후기 신고",
            description = "베이스캠프에서 함께했던 회원을 신고하거나, 내가 받은 동행 후기를 신고한다. 201과 함께 reportId와 status(RECEIVED)를 준다. "
                    + "targetMemberId, basecampId, kind, type, content는 필수이고 content는 1000자 이하다. "
                    + "kind가 MEMBER면 type은 NO_SHOW, MONEY_REQUEST, HARASSMENT_OR_THREAT, OFFENSIVE_BEHAVIOR, FAKE_PROFILE, "
                    + "ILLEGAL_CAMPING_INDUCEMENT 중 하나이고 companionReviewId를 넣을 수 없다. "
                    + "kind가 REVIEW면 type은 INAPPROPRIATE_REVIEW이고 companionReviewId가 필요하다. "
                    + "HARASSMENT_OR_THREAT를 접수하면 서버가 대상 회원을 즉시 72시간 임시 정지하고 그 회원의 세션을 모두 지운다. "
                    + "서버는 다음 순서로 검사하고 처음 걸린 이유로 응답한다. "
                    + "kind와 type이 맞지 않거나 companionReviewId가 kind와 맞지 않으면 400 INVALID_INPUT, "
                    + "자기 자신을 신고하거나 신고자나 대상이 그 베이스캠프에 신청하거나 멤버였던 적이 없으면(베이스캠프가 없을 때도) 403 REPORT_NOT_ELIGIBLE, "
                    + "후기 신고에서 그 후기가 해당 베이스캠프에서 대상이 내게 쓴 후기가 아니거나 숨김 처리되었거나 아직 블라인드 공개 전이면 403 REPORT_NOT_ELIGIBLE, "
                    + "같은 베이스캠프에서 같은 회원을 같은 kind로 이미 신고했으면 409 REPORT_DUPLICATED이다.")
    @PostMapping("/api/member-reports")
    @ResponseStatus(HttpStatus.CREATED)
    MemberReportCreatedResponse report(
            @AuthenticationPrincipal LoginMember loginMember, @Valid @RequestBody MemberReportRequest request) {
        long reportId = memberReportService.report(loginMember.memberId(), request.toCommand());
        return new MemberReportCreatedResponse(reportId, ReportStatus.RECEIVED);
    }
}
