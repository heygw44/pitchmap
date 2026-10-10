package com.pitchmap.community.api;

import com.pitchmap.common.security.LoginMember;
import com.pitchmap.community.application.CommunityReportService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CommunityReportController {

    private final CommunityReportService communityReportService;

    CommunityReportController(CommunityReportService communityReportService) {
        this.communityReportService = communityReportService;
    }

    @Operation(
            summary = "커뮤니티 글 신고",
            description = "이메일 인증을 마친 회원이 글을 신고하고, 201로 응답하며 본문은 없다. "
                    + "reason은 SPAM, ABUSE, ILLEGAL_CAMPING, PRIVACY, MONEY_SCAM, OTHER 중 하나이고 모르는 값은 400 INVALID_INPUT이다. "
                    + "content는 없어도 되고 1,000자 이하다. "
                    + "한 회원은 같은 글을 한 번만 신고할 수 있고, 이미 신고했으면 409 COMMUNITY_ALREADY_REPORTED로 응답한다. 작성자가 자기 글을 신고해도 막지 않는다. "
                    + "검토 전 신고가 5건 이상 쌓이면 서버가 같은 요청 안에서 글을 PENDING_REVIEW로 바꿔 목록과 상세에서 뺀다. "
                    + "글이 없거나 ACTIVE가 아니면(삭제, 숨김, 검토 대기) 404 NOT_FOUND로 응답한다. 그래서 검토 대기가 된 글은 더 신고할 수 없다. "
                    + "로그인하지 않았으면 401 AUTHENTICATION_REQUIRED, 이메일 인증을 마치지 않았으면 403 MEMBER_NOT_VERIFIED다.")
    @PostMapping("/api/community/posts/{postId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    void reportPost(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long postId,
            @Valid @RequestBody CommunityReportRequest request) {
        communityReportService.reportPost(loginMember.memberId(), postId, request.toCommand());
    }

    @Operation(
            summary = "커뮤니티 댓글 신고",
            description = "이메일 인증을 마친 회원이 댓글이나 답글을 신고하고, 201로 응답하며 본문은 없다. "
                    + "reason은 SPAM, ABUSE, ILLEGAL_CAMPING, PRIVACY, MONEY_SCAM, OTHER 중 하나이고 모르는 값은 400 INVALID_INPUT이다. "
                    + "content는 없어도 되고 1,000자 이하다. "
                    + "한 회원은 같은 댓글을 한 번만 신고할 수 있고, 이미 신고했으면 409 COMMUNITY_ALREADY_REPORTED로 응답한다. 작성자가 자기 댓글을 신고해도 막지 않는다. "
                    + "검토 전 신고가 5건 이상 쌓이면 서버가 같은 요청 안에서 댓글을 PENDING_REVIEW로 바꾼다. "
                    + "댓글이 없거나 ACTIVE가 아니거나(삭제, 숨김, 검토 대기) 달린 글이 ACTIVE가 아니면 404 NOT_FOUND로 응답한다. "
                    + "로그인하지 않았으면 401 AUTHENTICATION_REQUIRED, 이메일 인증을 마치지 않았으면 403 MEMBER_NOT_VERIFIED다.")
    @PostMapping("/api/community/comments/{commentId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    void reportComment(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long commentId,
            @Valid @RequestBody CommunityReportRequest request) {
        communityReportService.reportComment(loginMember.memberId(), commentId, request.toCommand());
    }
}
