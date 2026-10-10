package com.pitchmap.community.infra;

import java.time.Instant;

/**
 * 댓글 조회가 읽은 댓글 한 건과 작성자의 현재 닉네임. MyBatis가 이름으로 매핑하므로, 구성요소 이름은 열 별칭을 camelCase로 바꾼 것과 같아야 한다.
 *
 * <p>active는 댓글이 ACTIVE이면 true다. ACTIVE가 아닌 댓글은 내용이 이미 지워졌거나 보이면 안 되므로, 서비스가 active를 보고 자리만 남긴다.
 * parentId는 최상위 댓글이면 null이다.
 */
public record CommunityCommentRow(
        long commentId,
        Long parentId,
        boolean active,
        long authorId,
        String authorNickname,
        String content,
        Instant createdAt,
        Instant updatedAt) {}
