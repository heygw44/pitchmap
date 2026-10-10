package com.pitchmap.community.domain;

import java.util.Optional;

public interface CommunityPostRepository {

    CommunityPost saveAndFlush(CommunityPost post);

    Optional<CommunityPost> findById(Long id);

    /** 호출하면 행을 쓰기 잠금(SELECT ... FOR UPDATE)으로 읽는다. 트랜잭션 안에서만 부른다. */
    Optional<CommunityPost> findByIdForUpdate(long id);

    /** 지금 트랜잭션의 변경을 DB에 반영한다. 같은 트랜잭션에서 MyBatis로 읽기 전에 부른다. */
    void flush();
}
