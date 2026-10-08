package com.pitchmap.trust.api;

import com.pitchmap.common.security.LoginMember;
import com.pitchmap.trust.application.CompanionReviewCommandService;
import com.pitchmap.trust.application.CompanionReviewQueryService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CompanionReviewController {

    private final CompanionReviewCommandService companionReviewCommandService;
    private final CompanionReviewQueryService companionReviewQueryService;

    CompanionReviewController(
            CompanionReviewCommandService companionReviewCommandService,
            CompanionReviewQueryService companionReviewQueryService) {
        this.companionReviewCommandService = companionReviewCommandService;
        this.companionReviewQueryService = companionReviewQueryService;
    }

    @Operation(
            summary = "작성할 동행 후기 목록",
            description = "내가 최종 멤버(ACTIVE)인 완료 베이스캠프 중 작성 기한 안인 것을 마감이 빠른 순서로 배열로 돌려준다. "
                    + "각 항목은 basecampId, basecampTitle, completedAt, deadline, targets(memberId, nickname)이다. "
                    + "deadline은 완료 시각에서 14일 뒤이고, 그 시각 정각까지 쓸 수 있다. "
                    + "targets는 아직 후기를 쓰지 않은 다른 최종 멤버이고, 대상이 남지 않은 베이스캠프는 목록에서 빠진다. "
                    + "페이지로 나누지 않는다. 신뢰 단계가 1 미만이면 403 TRUST_LEVEL_INSUFFICIENT로 응답한다.")
    @GetMapping("/api/me/companion-reviews/pending")
    List<PendingCompanionReviewResponse> pending(@AuthenticationPrincipal LoginMember loginMember) {
        return companionReviewQueryService.pending(loginMember.memberId()).stream()
                .map(PendingCompanionReviewResponse::from)
                .toList();
    }

    @Operation(
            summary = "동행 후기 작성",
            description =
                    "완료된 베이스캠프의 최종 멤버가 같은 베이스캠프의 다른 최종 멤버에게 후기를 쓴다. 201과 함께 reviewId를 준다. "
                            + "revieweeId는 양수이고 rejoinWanted와 함께 필수다. tags는 없거나 0~7개이고 같은 값을 두 번 넣으면 400 INVALID_INPUT이다. "
                            + "tags의 값은 ON_TIME, LEAVE_NO_TRACE, CONSIDERATE, WELL_PREPARED, LATE, NO_SHOW, LITTERING이다. "
                            + "comment는 300자 이하이고 공백뿐이면 없는 것으로 저장한다. 쓴 후기는 고치거나 지울 수 없다. "
                            + "서버는 다음 순서로 검사하고 처음 걸린 이유로 응답한다. "
                            + "신뢰 단계가 1 미만이면 403 TRUST_LEVEL_INSUFFICIENT, 베이스캠프가 없으면 404 NOT_FOUND, "
                            + "완료되지 않았거나 작성자나 상대가 최종 멤버가 아니거나 자기 자신에게 쓰면 403 COMPANION_REVIEW_NOT_ELIGIBLE, "
                            + "작성 기한이 지났으면 400 COMPANION_REVIEW_DEADLINE_PASSED, 같은 상대에게 이미 썼으면 409 COMPANION_REVIEW_DUPLICATED이다.")
    @PostMapping("/api/basecamps/{basecampId}/companion-reviews")
    @ResponseStatus(HttpStatus.CREATED)
    CompanionReviewCreatedResponse write(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long basecampId,
            @Valid @RequestBody CompanionReviewWriteRequest request) {
        long reviewId = companionReviewCommandService.write(loginMember.memberId(), basecampId, request.toCommand());
        return new CompanionReviewCreatedResponse(reviewId);
    }

    @Operation(
            summary = "받은 동행 후기 목록",
            description = "내가 받은 후기를 작성 시각이 늦은 순서로 돌려준다. 작성 시각이 같으면 reviewId가 큰 것이 먼저다. 숨김 처리된 후기는 빠진다. "
                    + "내가 그 작성자에게 같은 베이스캠프의 후기를 썼거나 작성 기한이 지난 후기는 "
                    + "reviewId, basecampId, basecampTitle, reviewer(memberId, nickname), rejoinWanted, tags, comment, createdAt, revealed(true)로 응답한다. "
                    + "아직 공개 조건을 채우지 못한 후기는 basecampId와 revealed(false)만 응답하고, 나머지 필드는 null이 아니라 응답에서 빠진다. "
                    + "page는 0부터 시작하고 기본값은 0이다. size는 1~50이고 기본값은 20이다. "
                    + "응답에는 전체 개수가 없고 다음 페이지가 있는지만 hasNext로 알려 준다. 파라미터가 범위를 벗어나면 400 INVALID_INPUT으로 응답한다.")
    @GetMapping("/api/me/companion-reviews/received")
    CompanionReviewPageResponse<ReceivedCompanionReviewResponse> received(
            @AuthenticationPrincipal LoginMember loginMember,
            @Valid @ParameterObject @ModelAttribute CompanionReviewListRequest request) {
        return CompanionReviewPageResponse.from(
                companionReviewQueryService.received(
                        loginMember.memberId(), request.pageOrDefault(), request.sizeOrDefault()),
                ReceivedCompanionReviewResponse::from);
    }

    @Operation(
            summary = "회원이 받은 동행 후기 목록",
            description = "이메일 인증을 마친 회원이 다른 회원이 받은 후기를 작성 시각이 늦은 순서로 본다. 각 항목은 tags, comment, createdAt뿐이다. "
                    + "작성자와 개별 \"다시 동행\" 여부는 주지 않는다. 숨김 처리되었거나 작성 기한이 남아 있고 블라인드 공개 전인 후기는 빠진다. "
                    + "page는 0부터 시작하고 기본값은 0이다. size는 1~50이고 기본값은 20이다. "
                    + "파라미터가 범위를 벗어나면 400 INVALID_INPUT으로, 없거나 탈퇴한 회원이면 404 NOT_FOUND로 응답한다.")
    @GetMapping("/api/members/{memberId}/companion-reviews")
    CompanionReviewPageResponse<PublicCompanionReviewResponse> forMember(
            @PathVariable long memberId, @Valid @ParameterObject @ModelAttribute CompanionReviewListRequest request) {
        return CompanionReviewPageResponse.from(
                companionReviewQueryService.forMember(memberId, request.pageOrDefault(), request.sizeOrDefault()),
                PublicCompanionReviewResponse::from);
    }
}
