package com.pitchmap.spot.api;

import com.pitchmap.common.security.LoginMember;
import com.pitchmap.spot.application.BakjiCommandService;
import com.pitchmap.spot.application.BakjiFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bakjis")
class BakjiController {

    private final BakjiCommandService bakjiCommandService;
    private final BakjiFeedbackService bakjiFeedbackService;

    BakjiController(BakjiCommandService bakjiCommandService, BakjiFeedbackService bakjiFeedbackService) {
        this.bakjiCommandService = bakjiCommandService;
        this.bakjiFeedbackService = bakjiFeedbackService;
    }

    @Operation(
            summary = "박지 제보",
            description =
                    "이메일 인증을 마친 회원이 박지를 제보한다. 서버는 제보한 회원을 제보자로 기록하고, 좌표가 공원 경계 안인지 한 번 판정해서 결과를 저장한다. "
                            + "응답의 parkWarning은 경고 여부(warned)이고, 경고이면 공원 이름(areaName)을 함께 주며 경고가 아니면 areaName 필드를 뺀다. "
                            + "guide는 항상 주는 안내 문구이고 경고일 때와 아닐 때 문구가 다르다. "
                            + "duplicateCandidates는 제보한 좌표에서 50m 안에 이미 있는 박지를 가까운 순서로 최대 10개 준다(distanceM은 정수로 반올림한 미터). "
                            + "중복 후보가 있어도 제보는 막지 않는다. "
                            + "이름은 공백뿐일 수 없고 100자 이하이며, 설명은 2,000자 이하다. 위도는 -90~90, 경도는 -180~180이다. "
                            + "signalLevel은 NONE, WEAK, GOOD 중 하나이고, groundType은 SOIL, GRASS, GRAVEL, SAND, ROCK, DECK 중 하나다. "
                            + "좌표가 기상청 격자 범위 밖이면 lat와 lng에 필드 오류를 붙여 400 INVALID_INPUT으로 응답한다. 모르는 통신 상태나 바닥 유형도 400 INVALID_INPUT이다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    BakjiSubmissionResponse report(
            @AuthenticationPrincipal LoginMember loginMember, @Valid @RequestBody BakjiCreateRequest request) {
        return BakjiSubmissionResponse.from(bakjiCommandService.report(loginMember.memberId(), request.toCommand()));
    }

    @Operation(
            summary = "박지 수정",
            description = "제보자가 자기 박지를 고친다. 요청에 없는 필드는 그대로 두고, null로 보낸 설명·통신 상태·바닥 유형은 지운다. "
                    + "이름, 위도·경도, 물·화장실 유무는 null로 지울 수 없어서 400 INVALID_INPUT이다. 위도와 경도는 함께 보내야 하고, 하나만 보내도 400이다. "
                    + "좌표가 실제로 바뀔 때만 서버가 새 좌표로 공원 경계를 다시 판정한다. 새 좌표가 기상청 격자 범위 밖이면 400 INVALID_INPUT이다. "
                    + "응답은 박지 제보와 같고, 중복 후보도 수정한 뒤의 좌표로 계산한다. "
                    + "장소가 없거나, 박지가 아니거나, 지도에 보이는(ACTIVE) 상태가 아니면 404 NOT_FOUND로 응답한다. "
                    + "다른 회원이 제보한 박지이면 403 ACCESS_DENIED로 응답한다.")
    @PatchMapping("/{spotId}")
    BakjiSubmissionResponse update(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long spotId,
            @RequestBody BakjiUpdateRequest request) {
        return BakjiSubmissionResponse.from(
                bakjiCommandService.update(loginMember.memberId(), spotId, request.toCommand()));
    }

    @Operation(
            summary = "박지 삭제",
            description = "제보자가 자기 박지를 삭제한다. 서버는 행을 지우지 않고 상태를 DELETED로 바꿔 지도와 상세에서 숨기고, 204로 응답한다. "
                    + "장소가 없거나, 박지가 아니거나, 지도에 보이는(ACTIVE) 상태가 아니면 404 NOT_FOUND로 응답한다. "
                    + "다른 회원이 제보한 박지이면 403 ACCESS_DENIED로 응답한다.")
    @DeleteMapping("/{spotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal LoginMember loginMember, @PathVariable long spotId) {
        bakjiCommandService.delete(loginMember.memberId(), spotId);
    }

    @Operation(
            summary = "박지 확인",
            description = "이메일 인증을 마친 회원이 박지를 다녀왔고 정보가 맞다고 확인한다. 한 회원은 같은 박지를 한 번만 확인할 수 있고, "
                    + "이미 확인했으면 409 BAKJI_ALREADY_CONFIRMED로 응답한다. 제보자가 자기 박지를 확인해도 막지 않는다. "
                    + "응답은 201과 함께 확인한 뒤의 확인 수(confirmationCount)를 준다. "
                    + "장소가 없거나, 박지가 아니거나, 지도에 보이는(ACTIVE) 상태가 아니면 404 NOT_FOUND로 응답한다.")
    @PostMapping("/{spotId}/confirmations")
    @ResponseStatus(HttpStatus.CREATED)
    BakjiConfirmationResponse confirm(@AuthenticationPrincipal LoginMember loginMember, @PathVariable long spotId) {
        return new BakjiConfirmationResponse(bakjiFeedbackService.confirm(loginMember.memberId(), spotId));
    }

    @Operation(
            summary = "박지 신고",
            description = "이메일 인증을 마친 회원이 잘못된 박지를 신고하고, 201로 응답하며 본문은 없다. "
                    + "reason은 ILLEGAL_AREA, CLOSED, FALSE_INFO 중 하나이고 모르는 값은 400 INVALID_INPUT이다. content는 없어도 되고 1,000자 이하다. "
                    + "한 회원은 같은 박지를 한 번만 신고할 수 있고, 이미 신고했으면 409 BAKJI_ALREADY_REPORTED로 응답한다. 제보자가 자기 박지를 신고해도 막지 않는다. "
                    + "신고가 5건 이상 쌓이면 서버가 같은 요청 안에서 박지를 PENDING_REVIEW로 바꿔 지도, 목록, 상세에서 뺀다. "
                    + "장소가 없거나, 박지가 아니거나, 지도에 보이는(ACTIVE) 상태가 아니면 404 NOT_FOUND로 응답한다. 그래서 검토 대기가 된 박지는 더 신고할 수 없다.")
    @PostMapping("/{spotId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    void reportProblem(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long spotId,
            @Valid @RequestBody BakjiProblemReportRequest request) {
        bakjiFeedbackService.reportProblem(loginMember.memberId(), spotId, request.toCommand());
    }
}
