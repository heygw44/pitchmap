package com.pitchmap.admin.api;

import com.pitchmap.common.security.LoginMember;
import com.pitchmap.spot.application.AdminSpotQueryService;
import com.pitchmap.spot.application.SpotModerationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// /api/admin/** 경로는 보안 설정이 ADMIN 역할에만 연다.
@RestController
@RequestMapping("/api/admin/spots")
class AdminSpotController {

    private static final int MAX_PAGE_SIZE = 50;

    private final AdminSpotQueryService queryService;
    private final SpotModerationService moderationService;

    AdminSpotController(AdminSpotQueryService queryService, SpotModerationService moderationService) {
        this.queryService = queryService;
        this.moderationService = moderationService;
    }

    @Operation(
            summary = "박지 검토 목록",
            description = "status(PENDING_REVIEW 기본, 또는 HIDDEN)인 장소를 상태가 바뀐 시각이 이른 순서로 돌려준다. "
                    + "각 항목에는 제보자, 검토 전 신고의 수, 사유별 수, 최근 신고 5건의 내용을 담는다. 다른 status는 400 INVALID_INPUT이다. "
                    + "page는 0부터 세고, size는 기본 20, 1~50이다.")
    @GetMapping
    AdminSpotPageResponse list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return AdminSpotPageResponse.from(queryService.list(status, page, size));
    }

    @Operation(
            summary = "장소 숨김",
            description = "지도에 보이는(ACTIVE) 장소나 검토 대기(PENDING_REVIEW) 장소를 HIDDEN으로 바꾼다. 박지와 공공데이터 장소 모두 숨길 수 있다. "
                    + "장소가 없거나 제보자가 지운 박지이면 404 NOT_FOUND, 이미 숨긴 장소이면 409 SPOT_INVALID_STATE이다.")
    @PostMapping("/{id}/hide")
    SpotStatusResponse hide(@PathVariable long id, @AuthenticationPrincipal LoginMember admin) {
        return SpotStatusResponse.from(moderationService.hide(id, admin.memberId()));
    }

    @Operation(
            summary = "장소 복구",
            description = "검토 대기(PENDING_REVIEW)나 숨긴(HIDDEN) 장소를 ACTIVE로 바꾸고, 그 장소의 검토 전 신고를 모두 검토를 마친 것으로 표시한다. "
                    + "그래서 복구한 박지는 새 신고가 5건 쌓여야 다시 검토 대기가 된다. "
                    + "장소가 없거나 제보자가 지운 박지이면 404 NOT_FOUND, 이미 ACTIVE인 장소이면 409 SPOT_INVALID_STATE이다.")
    @PostMapping("/{id}/restore")
    SpotStatusResponse restore(@PathVariable long id, @AuthenticationPrincipal LoginMember admin) {
        return SpotStatusResponse.from(moderationService.restore(id, admin.memberId()));
    }
}
