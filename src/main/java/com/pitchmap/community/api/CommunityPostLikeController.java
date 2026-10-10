package com.pitchmap.community.api;

import com.pitchmap.common.security.LoginMember;
import com.pitchmap.community.application.CommunityPostLikeService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CommunityPostLikeController {

    private final CommunityPostLikeService communityPostLikeService;

    CommunityPostLikeController(CommunityPostLikeService communityPostLikeService) {
        this.communityPostLikeService = communityPostLikeService;
    }

    @Operation(
            summary = "커뮤니티 글 좋아요",
            description = "이메일 인증을 마친 회원이 글에 좋아요를 누르고, 200과 함께 liked(true)와 likeCount를 준다. "
                    + "이미 좋아요를 누른 글에 다시 보내도 서버는 오류 없이 같은 응답을 주고 좋아요 수는 늘지 않는다. "
                    + "글이 없거나 ACTIVE가 아니면(삭제, 숨김, 검토 대기) 404 NOT_FOUND다. "
                    + "로그인하지 않았으면 401 AUTHENTICATION_REQUIRED, 이메일 인증을 마치지 않았으면 403 MEMBER_NOT_VERIFIED다.")
    @PutMapping("/api/community/posts/{postId}/like")
    CommunityPostLikeResponse like(@AuthenticationPrincipal LoginMember loginMember, @PathVariable long postId) {
        return CommunityPostLikeResponse.from(communityPostLikeService.like(loginMember.memberId(), postId));
    }

    @Operation(
            summary = "커뮤니티 글 좋아요 취소",
            description = "이메일 인증을 마친 회원이 글에 눌러 둔 좋아요를 취소하고, 200과 함께 liked(false)와 likeCount를 준다. "
                    + "좋아요를 누르지 않은 글에 보내도 서버는 오류 없이 같은 응답을 준다. "
                    + "글이 없거나 ACTIVE가 아니면(삭제, 숨김, 검토 대기) 404 NOT_FOUND다. "
                    + "로그인하지 않았으면 401 AUTHENTICATION_REQUIRED, 이메일 인증을 마치지 않았으면 403 MEMBER_NOT_VERIFIED다.")
    @DeleteMapping("/api/community/posts/{postId}/like")
    CommunityPostLikeResponse unlike(@AuthenticationPrincipal LoginMember loginMember, @PathVariable long postId) {
        return CommunityPostLikeResponse.from(communityPostLikeService.unlike(loginMember.memberId(), postId));
    }
}
