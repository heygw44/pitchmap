package com.pitchmap.community.domain;

import java.util.Optional;

public interface CommunityCommentRepository {

    CommunityComment saveAndFlush(CommunityComment comment);

    Optional<CommunityComment> findById(Long id);

    /** 지금 트랜잭션의 변경을 DB에 반영한다. 같은 트랜잭션에서 MyBatis로 읽기 전에 부른다. */
    void flush();
}
