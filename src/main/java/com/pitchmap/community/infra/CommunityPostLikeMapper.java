package com.pitchmap.community.infra;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 좋아요는 엔티티 없이 행 하나를 넣고 지우는 일이 전부이고, 같은 회원이 동시에 눌러도 예외 없이 한 행만 남아야 한다.
// JPA로 저장하면 기본 키가 겹칠 때 예외가 나고 그 트랜잭션은 롤백 전용이 된다. 그래서 SQL을 직접 쓰는 이 매퍼로만 쓴다.
@Mapper
public interface CommunityPostLikeMapper {

    /** 호출하면 좋아요 행을 넣는다. 이미 있으면 아무것도 바꾸지 않고, 예외도 내지 않는다. */
    void insertIgnoringDuplicate(
            @Param("postId") long postId, @Param("memberId") long memberId, @Param("now") Instant now);

    /** 호출하면 좋아요 행을 지운다. 행이 없어도 예외 없이 0건이 지워진 것으로 끝난다. */
    void delete(@Param("postId") long postId, @Param("memberId") long memberId);

    /** 호출하면 글의 좋아요 수를 센다. */
    long countByPost(@Param("postId") long postId);
}
