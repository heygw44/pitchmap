package com.pitchmap.community.api;

import com.pitchmap.common.security.LoginMember;
import com.pitchmap.community.application.CommunityCommentCommandService;
import com.pitchmap.community.application.CommunityCommentQueryService;
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
class CommunityCommentController {

    private final CommunityCommentCommandService communityCommentCommandService;
    private final CommunityCommentQueryService communityCommentQueryService;

    CommunityCommentController(
            CommunityCommentCommandService communityCommentCommandService,
            CommunityCommentQueryService communityCommentQueryService) {
        this.communityCommentCommandService = communityCommentCommandService;
        this.communityCommentQueryService = communityCommentQueryService;
    }

    @Operation(
            summary = "커뮤니티 댓글 목록",
            description = "글의 댓글을 오래된 순으로 돌려준다. 로그인하지 않아도 조회할 수 있다. "
                    + "page는 0부터 시작하고 기본값은 0이다. size는 1~50이고 기본값은 20이다. 둘 다 답글이 아닌 댓글 단위로 센다. "
                    + "응답에는 전체 개수가 없고, 다음 페이지가 있는지만 hasNext로 알려 준다. "
                    + "각 항목은 commentId, 작성자(memberId, nickname), 내용(content), 삭제 여부(deleted), 작성 시각(createdAt), "
                    + "수정 시각(updatedAt), 답글 목록(replies)이다. replies는 그 댓글의 답글을 오래된 순으로 모두 담은 같은 모양의 배열이고, "
                    + "답글의 replies는 빈 배열이다. 작성자는 회원 ID와 닉네임만 주고, 탈퇴한 회원이면 익명 닉네임이 나온다. "
                    + "삭제·숨김·검토 대기 댓글은 보이는 답글이 있을 때만 commentId, deleted(true), replies만 담은 자리로 나오고, "
                    + "author·content·createdAt·updatedAt 필드는 뺀다. 보이는 답글이 없으면 목록에서 뺀다. "
                    + "글이 없거나 ACTIVE가 아니면 404 NOT_FOUND, 범위를 벗어난 page나 size는 400 INVALID_INPUT이다.")
    @GetMapping("/api/community/posts/{postId}/comments")
    CommunityCommentPageResponse list(
            @PathVariable long postId, @Valid @ParameterObject @ModelAttribute CommunityCommentListRequest request) {
        return CommunityCommentPageResponse.from(
                communityCommentQueryService.list(postId, request.pageOrDefault(), request.sizeOrDefault()));
    }

    @Operation(
            summary = "커뮤니티 댓글 작성",
            description = "이메일 인증을 마친 회원이 글에 댓글이나 답글을 쓴다. 201과 함께 commentId를 준다. "
                    + "content는 공백뿐일 수 없고 1,000자 이하이며, 어기면 400 INVALID_INPUT이다. "
                    + "parentId는 선택이고, 보내면 그 댓글에 다는 답글이 된다. 부모 댓글이 없거나, 다른 글의 댓글이거나, "
                    + "ACTIVE가 아니거나, 그 자체가 답글이면 400 INVALID_INPUT이다. "
                    + "글이 없거나 ACTIVE가 아니면 404 NOT_FOUND다.")
    @PostMapping("/api/community/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    CommunityCommentCreatedResponse write(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long postId,
            @Valid @RequestBody CommunityCommentCreateRequest request) {
        return new CommunityCommentCreatedResponse(
                communityCommentCommandService.write(loginMember.memberId(), postId, request.toCommand()));
    }

    @Operation(
            summary = "커뮤니티 댓글 수정",
            description = "작성자가 자기 댓글의 내용을 고친다. content는 공백뿐일 수 없고 1,000자 이하이며, 어기면 400 INVALID_INPUT이다. "
                    + "응답은 200과 함께 고친 뒤의 댓글 한 건이고, 모양은 댓글 목록의 항목과 같다. 최상위 댓글이면 보이는 답글도 replies에 담는다. "
                    + "댓글이 없거나 ACTIVE가 아니거나 댓글이 달린 글이 ACTIVE가 아니면 작성자 본인에게도 404 NOT_FOUND이고, "
                    + "다른 회원이 쓴 댓글이면 403 ACCESS_DENIED다.")
    @PatchMapping("/api/community/comments/{commentId}")
    CommunityCommentItemResponse update(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long commentId,
            @Valid @RequestBody CommunityCommentUpdateRequest request) {
        return CommunityCommentItemResponse.from(
                communityCommentCommandService.revise(loginMember.memberId(), commentId, request.content()));
    }

    @Operation(
            summary = "커뮤니티 댓글 삭제",
            description = "작성자가 자기 댓글을 삭제하고, 204로 응답한다. 서버는 상태를 DELETED로 바꾸고 내용을 지운 채 행은 남긴다. "
                    + "답글이 있으면 목록에 자리만 남는다. "
                    + "댓글이 없거나 ACTIVE가 아니거나 댓글이 달린 글이 ACTIVE가 아니면 작성자 본인에게도 404 NOT_FOUND이고, "
                    + "다른 회원이 쓴 댓글이면 403 ACCESS_DENIED다.")
    @DeleteMapping("/api/community/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal LoginMember loginMember, @PathVariable long commentId) {
        communityCommentCommandService.delete(loginMember.memberId(), commentId);
    }
}
