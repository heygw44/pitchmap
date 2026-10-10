package com.pitchmap.community.infra;

import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 댓글 목록은 작성자의 닉네임을 함께 보여 주고, 답글이 있는 최상위 댓글을 페이지 단위로 읽는다.
// JPA로 읽으면 댓글마다 회원을 따로 조회하거나, community 모듈이 member 모듈의 엔티티에 기대야 한다.
// 그래서 이 매퍼가 member를 조인하는 SQL을 직접 쓰고, 결과는 조회 전용 record로만 받는다.
@Mapper
public interface CommunityCommentMapper {

    /**
     * 호출하면 postId인 글의 최상위 댓글을 오래된 순(댓글 ID 오름차순)으로 offset건 건너뛰고 최대 limit건 읽는다.
     * 댓글이 ACTIVE가 아니어도 ACTIVE인 답글이 하나라도 있으면 읽는다. 답글이 없는 비ACTIVE 댓글은 읽지 않는다.
     */
    List<CommunityCommentRow> selectTopLevel(
            @Param("postId") long postId, @Param("offset") long offset, @Param("limit") int limit);

    /**
     * 호출하면 parentIds 댓글에 달린 ACTIVE 답글을 오래된 순으로 읽는다. parentIds가 비어 있으면 SQL이 틀리므로 호출하지 않는다.
     */
    List<CommunityCommentRow> selectActiveReplies(@Param("parentIds") List<Long> parentIds);

    /** 호출하면 commentId인 댓글 한 건을 읽는다. 상태는 따지지 않는다. 없으면 빈 값이다. */
    Optional<CommunityCommentRow> selectById(@Param("commentId") long commentId);
}
