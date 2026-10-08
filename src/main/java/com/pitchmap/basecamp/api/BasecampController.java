package com.pitchmap.basecamp.api;

import com.pitchmap.basecamp.application.BasecampApplicationCancelService;
import com.pitchmap.basecamp.application.BasecampApplicationListService;
import com.pitchmap.basecamp.application.BasecampApplyService;
import com.pitchmap.basecamp.application.BasecampApprovalService;
import com.pitchmap.basecamp.application.BasecampDetailQueryService;
import com.pitchmap.basecamp.application.BasecampEditService;
import com.pitchmap.basecamp.application.BasecampMembershipService;
import com.pitchmap.basecamp.application.BasecampOpenService;
import com.pitchmap.basecamp.application.BasecampSearchService;
import com.pitchmap.basecamp.application.BasecampTransitionService;
import com.pitchmap.common.security.LoginMember;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class BasecampController {

    private final BasecampOpenService basecampOpenService;
    private final BasecampSearchService basecampSearchService;
    private final BasecampDetailQueryService basecampDetailQueryService;
    private final BasecampApplyService basecampApplyService;
    private final BasecampApplicationCancelService basecampApplicationCancelService;
    private final BasecampApplicationListService basecampApplicationListService;
    private final BasecampApprovalService basecampApprovalService;
    private final BasecampMembershipService basecampMembershipService;
    private final BasecampTransitionService basecampTransitionService;
    private final BasecampEditService basecampEditService;

    @Operation(
            summary = "베이스캠프 열기",
            description = "본인확인을 마친 회원(신뢰 단계 1 이상)이 장소에 베이스캠프를 열고, 201과 함께 basecampId와 status(RECRUITING)를 준다. "
                    + "캠프 리더는 첫 멤버가 된다. 출발일은 내일부터 60일 뒤까지, 박 수는 1~3박, 정원은 캠프 리더를 포함해 2~6명이다. "
                    + "일정이 어긋나면 400 BASECAMP_SCHEDULE_INVALID, 정원이 어긋나면 400 BASECAMP_CAPACITY_INVALID로 응답한다. "
                    + "합류 조건(joinCondition)은 생략할 수 있다. 연령대는 하한·상한을 함께 보내야 하고 20~60의 십 단위이며, "
                    + "sameGenderOnly가 true이면 캠프 리더의 본인확인 성별과 같은 사람만 받는다. 값이 어긋나면 400 INVALID_INPUT이다. "
                    + "필수 값이 없거나 제목이 100자, 설명이 2,000자를 넘어도 400 INVALID_INPUT이다. "
                    + "신뢰 단계가 1 미만이면 403 TRUST_LEVEL_INSUFFICIENT로 응답한다. "
                    + "장소가 없거나 지도에 보이는(ACTIVE) 상태가 아니면 404 NOT_FOUND로 응답한다. "
                    + "공원 경계 경고가 붙은 박지이면 400 BASECAMP_WARNING_SPOT으로 응답한다. "
                    + "모집 중이거나 마감된 베이스캠프를 이미 3개 열었으면 400 BASECAMP_OPEN_LIMIT로 응답한다.")
    @PostMapping("/api/basecamps")
    @ResponseStatus(HttpStatus.CREATED)
    BasecampOpenResponse open(
            @AuthenticationPrincipal LoginMember loginMember, @Valid @RequestBody BasecampOpenRequest request) {
        return BasecampOpenResponse.from(basecampOpenService.open(loginMember.memberId(), request.toCommand()));
    }

    @Operation(
            summary = "베이스캠프 검색",
            description = "지도 영역이나 반경 안에서 모집 중인 베이스캠프를 출발일이 빠른 순서로 돌려준다. 로그인하지 않아도 조회할 수 있다. "
                    + "지역은 swLat, swLng, neLat, neLng 네 개(지도 영역) 또는 lat, lng, radiusKm 세 개(반경) 중 한 묶음만 모두 보내야 한다. "
                    + "위도는 -90~90, 경도는 -180~180이고, 영역은 남서쪽 값이 북동쪽 값보다 작아야 하며, 반경은 0보다 크고 50 이하여야 한다. "
                    + "fromDate와 toDate는 출발일의 범위(yyyy-MM-dd, 양 끝 포함)이고 하나만 보내도 된다. "
                    + "hasVacancy가 true이면 정원이 아직 차지 않은 베이스캠프만 돌려준다. "
                    + "지도에 보이는(ACTIVE) 장소의 모집 중(RECRUITING) 베이스캠프만 나오고, 출발일이 같으면 베이스캠프 ID 순서다. "
                    + "page는 0부터 시작하고 기본값은 0이다. size는 1~50이고 기본값은 20이다. "
                    + "응답에는 전체 개수가 없고, 다음 페이지가 있는지만 hasNext로 알려 준다. "
                    + "로그인한 요청자의 응답에는 항목마다 canApply와 unmetReasons가 있다. unmetReasons는 신청할 수 없는 이유 코드의 목록이고, "
                    + "신청할 수 있으면 빈 목록이며 그때 canApply는 true다. 비로그인 요청자의 응답에는 두 필드가 없다. "
                    + "이유 코드는 TRUST_LEVEL(신뢰 단계 부족), AGE_GROUP(본인확인한 연령대가 없거나 범위 밖), GENDER(본인확인한 성별이 없거나 "
                    + "동성만 받는 조건과 다름), DATE_CONFLICT(같은 기간에 확정된 다른 베이스캠프의 멤버), ALREADY_JOINED(이미 멤버이거나 대기 중인 신청이 있음), "
                    + "REAPPLY_NOT_ALLOWED(거절·탈퇴·강퇴된 적이 있음)이고, 이 순서로 나온다. "
                    + "지역이 없거나 두 묶음을 모두 보냈거나 일부만 보냈을 때, 값이 범위를 벗어났을 때, 반경이 50을 넘을 때, "
                    + "fromDate가 toDate보다 늦을 때, 날짜 형식이 틀렸을 때는 400 INVALID_INPUT으로 응답한다.")
    @GetMapping("/api/basecamps")
    BasecampSearchPageResponse search(
            @AuthenticationPrincipal LoginMember loginMember,
            @Valid @ParameterObject @ModelAttribute BasecampSearchRequest request) {
        Long viewerId = loginMember == null ? null : loginMember.memberId();
        return BasecampSearchPageResponse.from(basecampSearchService.search(viewerId, request.toQuery()));
    }

    @Operation(
            summary = "베이스캠프 상세",
            description = "베이스캠프 하나의 기본 정보, 합류 조건, 멤버, 요청자와의 관계(myRelation)를 돌려준다. 로그인하지 않아도 조회할 수 있고, "
                    + "모집 중이 아닌 베이스캠프(마감, 확정, 완료, 취소)도 조회할 수 있다. "
                    + "멤버는 ACTIVE 멤버를 캠프 리더 먼저, 그다음 합류한 순서로 준다. "
                    + "비로그인 요청자의 응답에서는 leader와 members에 memberId, nickname(members는 role 포함)만 있고, myRelation은 NONE이다. "
                    + "로그인한 요청자의 응답에서는 members에 연령대·성별(각각 본인확인한 값인지 verified로 구분), 신뢰 단계, "
                    + "완료한 동행 횟수가 더해지고, leader에는 신뢰 단계가 더해진다. 연령대는 TWENTIES, THIRTIES, FORTIES, FIFTIES, SIXTIES_PLUS 같은 이름이다. "
                    + "myRelation은 NONE, APPLICANT(대기 중인 신청이 있음), MEMBER, LEADER 중 하나다. "
                    + "contactInfo는 베이스캠프가 확정된 뒤부터 완료 후 7일까지 ACTIVE 멤버(캠프 리더 포함)에게만, 등록된 값이 있을 때만 응답에 나타난다. "
                    + "그 밖의 요청자에게는 필드 자체가 없다. safetyNotice는 항상 있다. 이메일과 출생연도는 어떤 경우에도 응답하지 않는다. "
                    + "베이스캠프가 없으면 404 NOT_FOUND로, basecampId가 숫자가 아니면 400 INVALID_INPUT으로 응답한다.")
    @GetMapping("/api/basecamps/{basecampId}")
    BasecampDetailResponse find(@AuthenticationPrincipal LoginMember loginMember, @PathVariable long basecampId) {
        Long viewerId = loginMember == null ? null : loginMember.memberId();
        return BasecampDetailResponse.from(basecampDetailQueryService.find(viewerId, basecampId), viewerId != null);
    }

    @Operation(
            summary = "베이스캠프 합류 신청",
            description = "본인확인을 마친 회원(신뢰 단계 1 이상)이 모집 중인 베이스캠프에 합류를 신청하고, 201과 함께 applicationId와 status(PENDING)를 준다. "
                    + "요청 본문과 message는 생략할 수 있고, message는 500자 이하여야 한다(넘으면 400 INVALID_INPUT). "
                    + "스스로 취소했던 신청은 같은 applicationId로 다시 PENDING이 된다. 캠프 리더에게 알릴 이벤트가 같은 트랜잭션에서 기록된다. "
                    + "다음 순서로 검사하고 처음 걸린 이유로 응답한다. "
                    + "베이스캠프가 없으면 404 NOT_FOUND, 신뢰 단계가 1 미만이면 403 TRUST_LEVEL_INSUFFICIENT, "
                    + "모집 중이 아니면 409 BASECAMP_INVALID_STATE, 이미 대기 중이거나 멤버(캠프 리더 포함)이면 409 BASECAMP_ALREADY_APPLIED, "
                    + "거절·탈퇴·강퇴된 적이 있으면 409 BASECAMP_REAPPLY_NOT_ALLOWED, "
                    + "합류 조건(최소 신뢰 단계 2, 본인확인한 연령대, 본인확인한 성별)을 충족하지 못하면 403 BASECAMP_CONDITION_NOT_MET, "
                    + "같은 기간에 확정된 다른 베이스캠프의 멤버이면 409 BASECAMP_DATE_CONFLICT, "
                    + "대기 신청이 이미 20건이면 409 BASECAMP_PENDING_LIMIT이다.")
    @PostMapping("/api/basecamps/{basecampId}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    BasecampApplyResponse apply(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long basecampId,
            @Valid @RequestBody(required = false) BasecampApplyRequest request) {
        BasecampApplyRequest body = request == null ? BasecampApplyRequest.empty() : request;
        return BasecampApplyResponse.from(
                basecampApplyService.apply(body.toCommand(basecampId, loginMember.memberId())));
    }

    @Operation(
            summary = "베이스캠프 합류 신청 취소",
            description = "본인확인을 마친 회원(신뢰 단계 1 이상)이 대기 중인 자기 합류 신청을 취소하고 204로 응답한다. "
                    + "모집 중이거나 마감된 베이스캠프에서만 할 수 있다. 취소한 신청은 같은 베이스캠프에 다시 낼 수 있다. "
                    + "베이스캠프가 없거나 내 신청이 없으면 404 NOT_FOUND, 신뢰 단계가 1 미만이면 403 TRUST_LEVEL_INSUFFICIENT, "
                    + "베이스캠프가 모집 중이거나 마감된 상태가 아니거나 신청이 대기 중이 아니면 409 BASECAMP_INVALID_STATE로 응답한다.")
    @DeleteMapping("/api/basecamps/{basecampId}/applications/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancelApplication(@AuthenticationPrincipal LoginMember loginMember, @PathVariable long basecampId) {
        basecampApplicationCancelService.cancel(basecampId, loginMember.memberId());
    }

    @Operation(
            summary = "베이스캠프 합류 신청 목록",
            description =
                    "캠프 리더가 자기 베이스캠프에 들어온 합류 신청을 신청이 오래된 순서로 돌려준다(신청 시각이 같으면 신청 ID 순서). "
                            + "status를 생략하면 결정을 기다리는 PENDING 신청만 주고, PENDING, APPROVED, REJECTED, CANCELED, EXPIRED 중 하나를 보내면 그 상태만 준다. "
                            + "page는 0부터 시작하고 기본값은 0이다. size는 1~50이고 기본값은 20이다. 응답에는 전체 개수가 없고 다음 페이지가 있는지만 hasNext로 알려 준다. "
                            + "항목마다 applicationId, status, message, appliedAt과 신청자 프로필(memberId, nickname, ageGroup, ageGroupVerified, gender, "
                            + "genderVerified, trustLevel, completedCompanions)이 있다. 이메일과 출생연도는 응답하지 않는다. "
                            + "베이스캠프가 없으면 404 NOT_FOUND, 캠프 리더가 아니면 403 ACCESS_DENIED, status가 허용 값이 아니거나 page·size가 범위를 벗어나면 400 INVALID_INPUT이다.")
    @GetMapping("/api/basecamps/{basecampId}/applications")
    BasecampApplicationPageResponse listApplications(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long basecampId,
            @Valid @ParameterObject @ModelAttribute BasecampApplicationListRequest request) {
        return BasecampApplicationPageResponse.from(
                basecampApplicationListService.list(basecampId, loginMember.memberId(), request.toQuery()));
    }

    @Operation(
            summary = "베이스캠프 합류 신청 승인",
            description = "캠프 리더가 대기 중인 합류 신청을 승인하고, 200과 함께 승인한 뒤의 인원(headcount)과 베이스캠프 상태(status)를 준다. "
                    + "신청자는 멤버가 되고, 승인해서 정원이 차면 베이스캠프는 자동으로 마감되어 status가 CLOSED다. 신청자에게 알릴 이벤트가 같은 트랜잭션에서 기록된다. "
                    + "다음 순서로 검사하고 처음 걸린 이유로 응답하며, 거부되면 신청은 대기 중으로 남는다. "
                    + "베이스캠프가 없으면 404 NOT_FOUND, 캠프 리더가 아니면 403 ACCESS_DENIED, 이 베이스캠프의 신청이 아니면 404 NOT_FOUND, "
                    + "모집 중이 아니거나 신청이 대기 중이 아니면 409 BASECAMP_INVALID_STATE, 정원이 이미 차 있으면 409 BASECAMP_FULL, "
                    + "신청자가 지금 합류 조건(최소 신뢰 단계, 본인확인한 연령대, 본인확인한 성별)을 충족하지 못하면 403 BASECAMP_CONDITION_NOT_MET, "
                    + "신청자가 같은 기간에 확정된 다른 베이스캠프의 멤버이면 409 BASECAMP_DATE_CONFLICT이다.")
    @PostMapping("/api/basecamps/{basecampId}/applications/{applicationId}/approve")
    BasecampApproveResponse approveApplication(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long basecampId,
            @PathVariable long applicationId) {
        return BasecampApproveResponse.from(
                basecampApprovalService.approve(basecampId, applicationId, loginMember.memberId()));
    }

    @Operation(
            summary = "베이스캠프 합류 신청 거절",
            description = "캠프 리더가 대기 중인 합류 신청을 거절하고, 200과 함께 applicationId와 status(REJECTED)를 준다. "
                    + "거절된 회원은 같은 베이스캠프에 다시 신청할 수 없다(409 BASECAMP_REAPPLY_NOT_ALLOWED). 신청자에게 알릴 이벤트가 같은 트랜잭션에서 기록된다. "
                    + "베이스캠프가 없으면 404 NOT_FOUND, 캠프 리더가 아니면 403 ACCESS_DENIED, 이 베이스캠프의 신청이 아니면 404 NOT_FOUND, "
                    + "모집 중이 아니거나 신청이 대기 중이 아니면 409 BASECAMP_INVALID_STATE로 응답한다.")
    @PostMapping("/api/basecamps/{basecampId}/applications/{applicationId}/reject")
    BasecampRejectResponse rejectApplication(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long basecampId,
            @PathVariable long applicationId) {
        return BasecampRejectResponse.from(
                basecampApprovalService.reject(basecampId, applicationId, loginMember.memberId()));
    }

    @Operation(
            summary = "베이스캠프 탈퇴",
            description = "본인확인을 마친 회원(신뢰 단계 1 이상)인 멤버가 베이스캠프에서 탈퇴하고 204로 응답한다. 탈퇴한 회원은 같은 베이스캠프에 다시 신청할 수 없다. "
                    + "확정된 뒤 출발 48시간 안에 탈퇴하면 임박 탈퇴로 기록된다. 정원이 차서 자동 마감된 베이스캠프는 빈자리가 생기면 다시 모집 중이 되고, "
                    + "캠프 리더가 직접 마감한 베이스캠프는 마감 그대로다. "
                    + "다음 순서로 검사하고 처음 걸린 이유로 응답한다. "
                    + "베이스캠프가 없으면 404 NOT_FOUND, 신뢰 단계가 1 미만이면 403 TRUST_LEVEL_INSUFFICIENT, "
                    + "베이스캠프가 완료되었거나 취소되었으면 409 BASECAMP_INVALID_STATE, ACTIVE 멤버가 아니면 404 NOT_FOUND, "
                    + "캠프 리더이면 409 BASECAMP_LEADER_CANNOT_LEAVE이다.")
    @DeleteMapping("/api/basecamps/{basecampId}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void leave(@AuthenticationPrincipal LoginMember loginMember, @PathVariable long basecampId) {
        basecampMembershipService.leave(basecampId, loginMember.memberId());
    }

    @Operation(
            summary = "베이스캠프 멤버 강퇴",
            description =
                    "캠프 리더가 멤버를 강퇴하고 204로 응답한다. 본문의 reason은 NO_CONTACT, CONDITION_MISMATCH, INAPPROPRIATE_BEHAVIOR, OTHER 중 하나여야 하고 "
                            + "없거나 다른 값이면 400 INVALID_INPUT이다. 확정되기 전(모집 중, 마감)에만 할 수 있다. "
                            + "강퇴된 회원은 같은 베이스캠프에 다시 신청할 수 없고, 강퇴된 회원에게 알릴 이벤트가 같은 트랜잭션에서 기록된다. "
                            + "정원이 차서 자동 마감된 베이스캠프는 빈자리가 생기면 다시 모집 중이 된다. "
                            + "다음 순서로 검사하고 처음 걸린 이유로 응답한다. "
                            + "베이스캠프가 없으면 404 NOT_FOUND, 캠프 리더가 아니면 403 ACCESS_DENIED, "
                            + "베이스캠프가 확정되었거나 그 뒤 상태이면 409 BASECAMP_INVALID_STATE, 대상이 ACTIVE 멤버가 아니면 404 NOT_FOUND, "
                            + "대상이 캠프 리더이면 409 BASECAMP_LEADER_CANNOT_LEAVE이다.")
    @PostMapping("/api/basecamps/{basecampId}/members/{memberId}/kick")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void kick(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long basecampId,
            @PathVariable long memberId,
            @Valid @RequestBody BasecampKickRequest request) {
        basecampMembershipService.kick(basecampId, memberId, loginMember.memberId(), request.toReason());
    }

    @Operation(
            summary = "베이스캠프 마감",
            description = "캠프 리더가 모집 중인 베이스캠프를 직접 마감하고, 200과 함께 basecampId와 status(CLOSED)를 준다. "
                    + "직접 마감한 베이스캠프는 빈자리가 생겨도 저절로 다시 열리지 않는다. 본문은 없다. "
                    + "다음 순서로 검사하고 처음 걸린 이유로 응답한다. "
                    + "베이스캠프가 없으면 404 NOT_FOUND, 캠프 리더가 아니면 403 ACCESS_DENIED, 모집 중이 아니면 409 BASECAMP_INVALID_STATE이다.")
    @PostMapping("/api/basecamps/{basecampId}/close")
    BasecampStatusResponse close(@AuthenticationPrincipal LoginMember loginMember, @PathVariable long basecampId) {
        return BasecampStatusResponse.from(basecampTransitionService.close(basecampId, loginMember.memberId()));
    }

    @Operation(
            summary = "베이스캠프 모집 재개",
            description = "캠프 리더가 마감된 베이스캠프의 모집을 다시 열고, 200과 함께 basecampId와 status(RECRUITING)를 준다. 본문은 없다. "
                    + "다음 순서로 검사하고 처음 걸린 이유로 응답한다. "
                    + "베이스캠프가 없으면 404 NOT_FOUND, 캠프 리더가 아니면 403 ACCESS_DENIED, 마감 상태가 아니면 409 BASECAMP_INVALID_STATE, "
                    + "정원이 가득 차 있으면 409 BASECAMP_FULL이다.")
    @PostMapping("/api/basecamps/{basecampId}/reopen")
    BasecampStatusResponse reopen(@AuthenticationPrincipal LoginMember loginMember, @PathVariable long basecampId) {
        return BasecampStatusResponse.from(basecampTransitionService.reopen(basecampId, loginMember.memberId()));
    }

    @Operation(
            summary = "베이스캠프 확정",
            description = "캠프 리더가 모집 중이거나 마감된 베이스캠프를 확정하고, 200과 함께 basecampId와 status(CONFIRMED)를 준다. "
                    + "남은 대기 신청은 EXPIRED가 되고, 캠프 리더를 포함한 ACTIVE 멤버 전원에게 알릴 이벤트가 같은 트랜잭션에서 기록된다. 본문은 없다. "
                    + "다음 순서로 검사하고 처음 걸린 이유로 응답한다. "
                    + "베이스캠프가 없으면 404 NOT_FOUND, 캠프 리더가 아니면 403 ACCESS_DENIED, 모집 중도 마감도 아니면 409 BASECAMP_INVALID_STATE, "
                    + "인원이 2명 미만이면 409 BASECAMP_NOT_ENOUGH_MEMBERS이다.")
    @PostMapping("/api/basecamps/{basecampId}/confirm")
    BasecampStatusResponse confirm(@AuthenticationPrincipal LoginMember loginMember, @PathVariable long basecampId) {
        return BasecampStatusResponse.from(basecampTransitionService.confirm(basecampId, loginMember.memberId()));
    }

    @Operation(
            summary = "베이스캠프 취소",
            description =
                    "캠프 리더가 베이스캠프를 취소하고, 200과 함께 basecampId와 status(CANCELED)를 준다. 확정된 뒤에도 취소할 수 있다. "
                            + "취소 사유는 LEADER로 저장하고, 남은 대기 신청은 EXPIRED가 되며, 캠프 리더를 뺀 ACTIVE 멤버에게 알릴 이벤트가 같은 트랜잭션에서 기록된다. 본문은 없다. "
                            + "다음 순서로 검사하고 처음 걸린 이유로 응답한다. "
                            + "베이스캠프가 없으면 404 NOT_FOUND, 캠프 리더가 아니면 403 ACCESS_DENIED, 이미 완료되었거나 취소되었으면 409 BASECAMP_INVALID_STATE이다.")
    @PostMapping("/api/basecamps/{basecampId}/cancel")
    BasecampStatusResponse cancel(@AuthenticationPrincipal LoginMember loginMember, @PathVariable long basecampId) {
        return BasecampStatusResponse.from(basecampTransitionService.cancel(basecampId, loginMember.memberId()));
    }

    @Operation(
            summary = "베이스캠프 연락 수단 등록",
            description = "캠프 리더가 연락 수단을 등록하거나 바꾸고, 200과 함께 basecampId와 status를 준다. "
                    + "contactInfo는 필수이고 255자 이하의 https URL이어야 하며, 어긋나면 400 INVALID_INPUT이다. "
                    + "연락 수단은 베이스캠프가 확정된 뒤부터 완료 후 7일까지 ACTIVE 멤버에게만 상세 응답에 나타난다. "
                    + "다음 순서로 검사하고 처음 걸린 이유로 응답한다. "
                    + "요청 값이 어긋나면 400 INVALID_INPUT, 베이스캠프가 없으면 404 NOT_FOUND, 캠프 리더가 아니면 403 ACCESS_DENIED, "
                    + "완료되었거나 취소되었으면 409 BASECAMP_INVALID_STATE이다.")
    @PutMapping("/api/basecamps/{basecampId}/contact")
    BasecampStatusResponse registerContact(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long basecampId,
            @Valid @RequestBody BasecampContactRequest request) {
        return BasecampStatusResponse.from(
                basecampEditService.registerContact(basecampId, loginMember.memberId(), request.contactInfo()));
    }

    @Operation(
            summary = "베이스캠프 수정",
            description =
                    "캠프 리더가 title, description, joinCondition, capacity 가운데 보낸 필드만 고치고, 200과 함께 basecampId와 status를 준다. "
                            + "한 필드도 보내지 않으면 400 INVALID_INPUT이다. 보내지 않은 필드는 그대로 둔다. "
                            + "joinCondition을 보내면 합류 조건 전체를 바꾸며 규칙은 열 때와 같다(sameGenderOnly는 캠프 리더의 본인확인 성별을 쓴다). "
                            + "정원은 캠프 리더를 포함해 2~6명이고 늘리기만 할 수 있다. 정원이 차서 자동 마감된 베이스캠프는 정원을 늘리면 다시 모집 중이 된다. "
                            + "다음 순서로 검사하고 처음 걸린 이유로 응답한다. "
                            + "요청 값이 어긋나면 400 INVALID_INPUT, 베이스캠프가 없으면 404 NOT_FOUND, 캠프 리더가 아니면 403 ACCESS_DENIED, "
                            + "정원이 2~6명이 아니면 400 BASECAMP_CAPACITY_INVALID, 합류 조건 값이 올바르지 않으면 400 INVALID_INPUT, "
                            + "확정된 뒤이면 409 BASECAMP_INVALID_STATE, 정원을 줄이면 400 BASECAMP_CAPACITY_INVALID이다.")
    @PatchMapping("/api/basecamps/{basecampId}")
    BasecampStatusResponse revise(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long basecampId,
            @Valid @RequestBody BasecampReviseRequest request) {
        return BasecampStatusResponse.from(
                basecampEditService.revise(basecampId, loginMember.memberId(), request.toCommand()));
    }
}
