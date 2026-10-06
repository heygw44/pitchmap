package com.pitchmap.review.api;

import com.pitchmap.common.security.LoginMember;
import com.pitchmap.review.application.SpotReviewCommandService;
import com.pitchmap.review.application.SpotReviewQueryService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SpotReviewController {

    private final SpotReviewCommandService spotReviewCommandService;
    private final SpotReviewQueryService spotReviewQueryService;

    SpotReviewController(
            SpotReviewCommandService spotReviewCommandService, SpotReviewQueryService spotReviewQueryService) {
        this.spotReviewCommandService = spotReviewCommandService;
        this.spotReviewQueryService = spotReviewQueryService;
    }

    @Operation(
            summary = "장소 후기 목록",
            description = "장소의 후기를 작성 시각이 늦은 순서로 돌려준다. 작성 시각이 같으면 reviewId가 큰 것이 먼저다. 로그인하지 않아도 조회할 수 있다. "
                    + "각 항목은 reviewId, 작성자(memberId, nickname), 방문일(visitedDate), 평점(rating), 내용(content), 작성 시각(createdAt)이다. "
                    + "작성자는 회원 ID와 닉네임만 주고, 탈퇴한 회원이면 익명 닉네임이 나온다. "
                    + "page는 0부터 시작하고 기본값은 0이다. size는 1~50이고 기본값은 20이다. "
                    + "응답에는 전체 개수가 없고, 다음 페이지가 있는지만 hasNext로 알려 준다. "
                    + "파라미터가 범위를 벗어나면 400 INVALID_INPUT으로 응답한다. "
                    + "장소가 없거나 지도에 보이는(ACTIVE) 상태가 아니면 404 NOT_FOUND로 응답한다.")
    @GetMapping("/api/spots/{spotId}/reviews")
    SpotReviewPageResponse list(
            @PathVariable long spotId, @Valid @ParameterObject @ModelAttribute SpotReviewListRequest request) {
        return SpotReviewPageResponse.from(
                spotReviewQueryService.list(spotId, request.pageOrDefault(), request.sizeOrDefault()));
    }

    @Operation(
            summary = "장소 후기 작성",
            description = "이메일 인증을 마친 회원이 장소에 방문일, 평점, 내용으로 후기를 쓴다. 201과 함께 reviewId를 준다. "
                    + "방문일은 한국 날짜이고 오늘보다 뒤이면 400 INVALID_INPUT이다. 평점은 1~5 정수이고, "
                    + "내용은 공백뿐일 수 없으며 2,000자 이하다. 어기면 400 INVALID_INPUT이다. "
                    + "한 회원은 같은 장소에 방문일마다 후기를 하나만 쓸 수 있고, 같은 방문일로 이미 썼으면 409 SPOT_REVIEW_DUPLICATED로 응답한다. "
                    + "방문일이 다르면 같은 장소에 후기를 더 쓸 수 있다. "
                    + "장소가 없거나 지도에 보이는(ACTIVE) 상태가 아니면 404 NOT_FOUND로 응답한다. "
                    + "평균 평점과 후기 수는 저장하지 않고, 장소 상세가 조회할 때 계산한다.")
    @PostMapping("/api/spots/{spotId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    SpotReviewCreatedResponse write(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long spotId,
            @Valid @RequestBody SpotReviewCreateRequest request) {
        long reviewId = spotReviewCommandService.write(loginMember.memberId(), spotId, request.toCommand());
        return new SpotReviewCreatedResponse(reviewId);
    }

    @Operation(
            summary = "장소 후기 수정",
            description = "작성자가 자기 후기의 평점과 내용을 고친다. rating과 content를 둘 다 보내야 하고, 방문일은 바꿀 수 없다. "
                    + "평점은 1~5 정수이고, 내용은 공백뿐일 수 없으며 2,000자 이하다. 어기면 400 INVALID_INPUT이다. "
                    + "응답은 200과 함께 고친 뒤의 후기 한 건이고, 모양은 후기 목록의 항목과 같다. "
                    + "후기가 없으면 404 NOT_FOUND로, 다른 회원이 쓴 후기이면 403 ACCESS_DENIED로 응답한다. "
                    + "서버는 장소 상태를 보지 않으므로, 장소가 숨겨진 뒤에도 작성자는 후기를 고칠 수 있다.")
    @PatchMapping("/api/reviews/{reviewId}")
    SpotReviewItemResponse update(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long reviewId,
            @Valid @RequestBody SpotReviewUpdateRequest request) {
        return SpotReviewItemResponse.from(
                spotReviewCommandService.revise(loginMember.memberId(), reviewId, request.toCommand()));
    }

    @Operation(
            summary = "장소 후기 삭제",
            description = "작성자가 자기 후기를 삭제하고, 204로 응답한다. 서버는 후기 행을 지운다. "
                    + "후기가 없으면 404 NOT_FOUND로, 다른 회원이 쓴 후기이면 403 ACCESS_DENIED로 응답한다. "
                    + "서버는 장소 상태를 보지 않으므로, 장소가 숨겨진 뒤에도 작성자는 후기를 지울 수 있다.")
    @DeleteMapping("/api/reviews/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal LoginMember loginMember, @PathVariable long reviewId) {
        spotReviewCommandService.delete(loginMember.memberId(), reviewId);
    }
}
