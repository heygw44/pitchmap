package com.pitchmap.community.api;

import com.pitchmap.common.security.LoginMember;
import com.pitchmap.community.application.CommunityPostCommandService;
import com.pitchmap.community.application.CommunityPostQueryService;
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
class CommunityPostController {

    private final CommunityPostCommandService communityPostCommandService;
    private final CommunityPostQueryService communityPostQueryService;

    CommunityPostController(
            CommunityPostCommandService communityPostCommandService,
            CommunityPostQueryService communityPostQueryService) {
        this.communityPostCommandService = communityPostCommandService;
        this.communityPostQueryService = communityPostQueryService;
    }

    @Operation(
            summary = "커뮤니티 글 목록",
            description = "삭제·숨김·검토 대기가 아닌(ACTIVE) 글을 최신 글부터 돌려준다. 로그인하지 않아도 조회할 수 있다. "
                    + "spotId는 선택이고, 보내면 그 장소에 연결한 글만 준다. "
                    + "popular=true를 보내면 좋아요를 5개 이상 받은 인기글만 최신순으로 주고, 전체 글 수와 페이지 수도 인기글로 센다. "
                    + "spotId로 거를 때는 그 장소가 지금 지도에 보이는지 따지지 않는다. "
                    + "각 항목은 postId, 제목(title), 본문 앞 100자(excerpt), 작성자(memberId, nickname), "
                    + "연결한 장소(spotId, name), 좋아요 수(likeCount), 댓글 수(commentCount), 작성 시각(createdAt)이다. "
                    + "작성자는 회원 ID와 닉네임만 주고, 탈퇴한 회원이면 익명 닉네임이 나온다. "
                    + "연결한 장소가 없거나 지도에 보이는(ACTIVE) 상태가 아니면 spot 필드를 뺀다. "
                    + "page는 0부터 시작하고 기본값은 0이다. size는 1~50이고 기본값은 20이다. "
                    + "응답에는 다음 페이지가 있는지(hasNext)와 함께 조건에 맞는 전체 글 수(totalElements)와 페이지 수(totalPages)를 준다. "
                    + "마지막 페이지 너머를 요청하면 빈 content와 올바른 totalPages를 준다. "
                    + "범위를 벗어난 파라미터는 400 INVALID_INPUT으로 응답한다.")
    @GetMapping("/api/community/posts")
    CommunityPostPageResponse list(@Valid @ParameterObject @ModelAttribute CommunityPostListRequest request) {
        return CommunityPostPageResponse.from(
                communityPostQueryService.list(request.toQuery(), request.pageOrDefault(), request.sizeOrDefault()));
    }

    @Operation(
            summary = "커뮤니티 글 상세",
            description = "글 한 건을 본문 전체와 함께 돌려준다. 로그인하지 않아도 조회할 수 있다. "
                    + "필드는 postId, title, content, 작성자(memberId, nickname), 연결한 장소(spotId, name), "
                    + "좋아요 수(likeCount), 댓글 수(commentCount), 작성 시각(createdAt), 수정 시각(updatedAt)이다. 연결한 장소가 없거나 지도에 보이지 않으면 spot 필드를 뺀다. "
                    + "로그인한 회원에게는 그 회원이 좋아요를 눌렀는지(likedByMe)도 주고, 로그인하지 않았으면 이 필드를 뺀다. "
                    + "글이 없거나 ACTIVE가 아니면(삭제, 숨김, 검토 대기) 404 NOT_FOUND로 응답한다.")
    @GetMapping("/api/community/posts/{postId}")
    CommunityPostDetailResponse detail(@AuthenticationPrincipal LoginMember loginMember, @PathVariable long postId) {
        Long viewerId = loginMember == null ? null : loginMember.memberId();
        return CommunityPostDetailResponse.from(communityPostQueryService.detail(postId, viewerId));
    }

    @Operation(
            summary = "커뮤니티 글 작성",
            description = "이메일 인증을 마친 회원이 제목과 본문으로 글을 쓴다. 글은 분류 없이 한 게시판에 올라간다. 201과 함께 postId를 준다. "
                    + "제목은 공백뿐일 수 없고 100자 이하이며, "
                    + "본문은 공백뿐일 수 없고 10,000자 이하다. 어기면 400 INVALID_INPUT이다. "
                    + "spotId는 선택이다. 보내면 그 장소가 지도에 보이는(ACTIVE) 상태여야 하고, 없거나 ACTIVE가 아니면 400 INVALID_INPUT이다.")
    @PostMapping("/api/community/posts")
    @ResponseStatus(HttpStatus.CREATED)
    CommunityPostCreatedResponse write(
            @AuthenticationPrincipal LoginMember loginMember, @Valid @RequestBody CommunityPostCreateRequest request) {
        return new CommunityPostCreatedResponse(
                communityPostCommandService.write(loginMember.memberId(), request.toCommand()));
    }

    @Operation(
            summary = "커뮤니티 글 수정",
            description = "작성자가 자기 글에서 보낸 필드만 고친다. title, content, spotId, imageIds가 모두 선택이고, "
                    + "보내지 않은 필드는 그대로 둔다. 필드를 하나도 보내지 않으면({}) 아무것도 바꾸지 않고 200으로 응답한다. "
                    + "spotId를 null로 보내면 장소 연결을 끊고, 값을 보내면 그 장소가 ACTIVE여야 한다. "
                    + "title, content를 null로 보내거나, 제목·본문이 공백뿐이거나 길이 제한(제목 100자, 본문 10,000자)을 넘거나, "
                    + "장소가 없거나 ACTIVE가 아니면 400 INVALID_INPUT이다. "
                    + "응답은 200과 함께 고친 뒤의 글 상세이고, 모양은 글 상세와 같다. "
                    + "글이 없거나 ACTIVE가 아니면 작성자 본인에게도 404 NOT_FOUND이고, 다른 회원이 쓴 글이면 403 ACCESS_DENIED다.")
    @PatchMapping("/api/community/posts/{postId}")
    CommunityPostDetailResponse update(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long postId,
            @Valid @RequestBody CommunityPostUpdateRequest request) {
        return CommunityPostDetailResponse.from(
                communityPostCommandService.revise(loginMember.memberId(), postId, request.toCommand()));
    }

    @Operation(
            summary = "커뮤니티 글 삭제",
            description = "작성자가 자기 글을 삭제하고, 204로 응답한다. 서버는 상태만 DELETED로 바꾸고 행은 남긴다. "
                    + "글이 없거나 ACTIVE가 아니면 작성자 본인에게도 404 NOT_FOUND이고, 다른 회원이 쓴 글이면 403 ACCESS_DENIED다.")
    @DeleteMapping("/api/community/posts/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal LoginMember loginMember, @PathVariable long postId) {
        communityPostCommandService.delete(loginMember.memberId(), postId);
    }
}
