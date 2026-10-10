package com.pitchmap.community.domain;

import java.util.Collection;
import java.util.List;

public interface CommunityImageRepository {

    CommunityImage saveAndFlush(CommunityImage image);

    /** 호출하면 ids인 이미지를 잠그지 않고 읽는다. 없는 ID는 결과에 없다. */
    List<CommunityImage> findAllById(Iterable<Long> ids);

    /** 호출하면 행을 쓰기 잠금(SELECT ... FOR UPDATE)으로 읽는다. 트랜잭션 안에서만 부른다. 없는 ID는 결과에 없다. */
    List<CommunityImage> findAllByIdForUpdate(Collection<Long> ids);

    /** 호출하면 postId인 글에 붙은 이미지의 ID만 글 안 순서대로 읽는다. 엔티티를 영속성 컨텍스트에 올리지 않는다. */
    List<Long> findIdsByPostId(long postId);
}
