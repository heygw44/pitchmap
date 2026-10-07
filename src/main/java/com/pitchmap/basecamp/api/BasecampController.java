package com.pitchmap.basecamp.api;

import com.pitchmap.basecamp.application.BasecampOpenService;
import com.pitchmap.common.security.LoginMember;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class BasecampController {

    private final BasecampOpenService basecampOpenService;

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
}
