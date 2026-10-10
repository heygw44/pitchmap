package com.pitchmap.admin.api;

import com.pitchmap.common.security.LoginMember;
import com.pitchmap.community.application.AdminCommunityQueryService;
import com.pitchmap.community.application.CommunityModerationService;
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
@RequestMapping("/api/admin/community")
class AdminCommunityController {

    private static final int MAX_PAGE_SIZE = 50;

    private final AdminCommunityQueryService queryService;
    private final CommunityModerationService moderationService;

    AdminCommunityController(AdminCommunityQueryService queryService, CommunityModerationService moderationService) {
        this.queryService = queryService;
        this.moderationService = moderationService;
    }

    @Operation(
            summary = "커뮤니티 글 검토 목록",
            description = "status(PENDING_REVIEW 기본, 또는 HIDDEN)인 글을 돌려준다. 검토 전 신고 중 금전 요구·사기(MONEY_SCAM)가 있는 글이 먼저이고, "
                    + "같은 묶음에서는 상태가 바뀐 시각이 이른 순서다. 각 항목에는 작성자, 본문 전체, 검토 전 신고의 수, 사유별 수, 최근 신고 5건의 내용을 담는다. "
                    + "다른 status는 400 INVALID_INPUT이다. page는 0부터 세고, size는 기본 20, 1~50이다.")
    @GetMapping("/posts")
    AdminCommunityPageResponse<AdminCommunityPostResponse> listPosts(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return AdminCommunityPageResponse.from(
                queryService.listPosts(status, page, size), AdminCommunityPostResponse::from);
    }

    @Operation(
            summary = "커뮤니티 글 숨김",
            description = "ACTIVE나 검토 대기(PENDING_REVIEW) 글을 HIDDEN으로 바꾼다. 신고가 없는 글도 바로 숨길 수 있다. "
                    + "글이 없거나 작성자가 지운 글이면 404 NOT_FOUND, 이미 숨긴 글이면 409 COMMUNITY_INVALID_STATE이다.")
    @PostMapping("/posts/{postId}/hide")
    CommunityPostStatusResponse hidePost(@PathVariable long postId, @AuthenticationPrincipal LoginMember admin) {
        return CommunityPostStatusResponse.from(moderationService.hidePost(postId, admin.memberId()));
    }

    @Operation(
            summary = "커뮤니티 글 복구",
            description = "검토 대기(PENDING_REVIEW)나 숨긴(HIDDEN) 글을 ACTIVE로 바꾸고, 그 글의 검토 전 신고를 모두 검토를 마친 것으로 표시한다. "
                    + "그래서 복구한 글은 새 신고가 5건 쌓여야 다시 검토 대기가 된다. "
                    + "글이 없거나 작성자가 지운 글이면 404 NOT_FOUND, 이미 ACTIVE인 글이면 409 COMMUNITY_INVALID_STATE이다.")
    @PostMapping("/posts/{postId}/restore")
    CommunityPostStatusResponse restorePost(@PathVariable long postId, @AuthenticationPrincipal LoginMember admin) {
        return CommunityPostStatusResponse.from(moderationService.restorePost(postId, admin.memberId()));
    }

    @Operation(
            summary = "커뮤니티 댓글 검토 목록",
            description = "글 검토 목록과 같은 파라미터와 정렬로 댓글을 돌려준다. 항목에는 댓글이 달린 글의 postId, 작성자, 내용 전체, 검토 전 신고 정보를 담는다. "
                    + "작성자가 지운 댓글의 content는 null이다. 다른 status는 400 INVALID_INPUT이다.")
    @GetMapping("/comments")
    AdminCommunityPageResponse<AdminCommunityCommentResponse> listComments(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return AdminCommunityPageResponse.from(
                queryService.listComments(status, page, size), AdminCommunityCommentResponse::from);
    }

    @Operation(
            summary = "커뮤니티 댓글 숨김",
            description = "ACTIVE나 검토 대기(PENDING_REVIEW) 댓글을 HIDDEN으로 바꾼다. 달린 글의 상태는 보지 않는다. "
                    + "댓글이 없거나 작성자가 지운 댓글이면 404 NOT_FOUND, 이미 숨긴 댓글이면 409 COMMUNITY_INVALID_STATE이다.")
    @PostMapping("/comments/{commentId}/hide")
    CommunityCommentStatusResponse hideComment(
            @PathVariable long commentId, @AuthenticationPrincipal LoginMember admin) {
        return CommunityCommentStatusResponse.from(moderationService.hideComment(commentId, admin.memberId()));
    }

    @Operation(
            summary = "커뮤니티 댓글 복구",
            description = "검토 대기(PENDING_REVIEW)나 숨긴(HIDDEN) 댓글을 ACTIVE로 바꾸고, 그 댓글의 검토 전 신고를 모두 검토를 마친 것으로 표시한다. "
                    + "댓글이 없거나 작성자가 지운 댓글이면 404 NOT_FOUND, 이미 ACTIVE인 댓글이면 409 COMMUNITY_INVALID_STATE이다.")
    @PostMapping("/comments/{commentId}/restore")
    CommunityCommentStatusResponse restoreComment(
            @PathVariable long commentId, @AuthenticationPrincipal LoginMember admin) {
        return CommunityCommentStatusResponse.from(moderationService.restoreComment(commentId, admin.memberId()));
    }
}
