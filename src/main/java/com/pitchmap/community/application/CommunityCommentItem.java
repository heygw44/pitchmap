package com.pitchmap.community.application;

import com.pitchmap.community.infra.CommunityCommentRow;
import java.time.Instant;
import java.util.List;

/**
 * 댓글 한 건과 그 댓글에 달린 ACTIVE 답글. authorNickname은 조회한 시점의 닉네임이라서, 탈퇴한 회원이면 익명 닉네임이다.
 *
 * <p>deleted가 true인 항목은 삭제·숨김·검토 대기 댓글이 답글 때문에 자리만 남은 것이다. 이때 작성자, 내용, 시각은 모두 null이다.
 * 답글의 replies는 빈 목록이다.
 */
public record CommunityCommentItem(
        long commentId,
        boolean deleted,
        Long authorId,
        String authorNickname,
        String content,
        Instant createdAt,
        Instant updatedAt,
        List<CommunityCommentItem> replies) {

    static CommunityCommentItem from(CommunityCommentRow row, List<CommunityCommentItem> replies) {
        if (!row.active()) {
            return new CommunityCommentItem(row.commentId(), true, null, null, null, null, null, replies);
        }
        return new CommunityCommentItem(
                row.commentId(),
                false,
                row.authorId(),
                row.authorNickname(),
                row.content(),
                row.createdAt(),
                row.updatedAt(),
                replies);
    }
}
