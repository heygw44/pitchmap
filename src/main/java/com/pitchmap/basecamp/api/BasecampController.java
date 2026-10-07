package com.pitchmap.basecamp.api;

import com.pitchmap.basecamp.application.BasecampApplicationCancelService;
import com.pitchmap.basecamp.application.BasecampApplyService;
import com.pitchmap.basecamp.application.BasecampDetailQueryService;
import com.pitchmap.basecamp.application.BasecampOpenService;
import com.pitchmap.basecamp.application.BasecampSearchService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
}
