package com.pitchmap.community.infra;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 글 상세는 이미지를 글 안 순서대로 읽고, 정리 작업은 행을 한 건씩 조건부로 지운다. 둘 다 쿼리를 직접 쓰는 편이 분명해서 MyBatis를 쓴다.
@Mapper
public interface CommunityImageMapper {

    /** 호출하면 postId인 글에 붙은 이미지를 글 안 순서대로 읽는다. 이미지가 없으면 빈 목록이다. */
    List<CommunityImageRow> selectByPostId(@Param("postId") long postId);

    /** 호출하면 글에 붙지 않았고 createdBefore보다 먼저 만든 이미지를 ID 오름차순으로 최대 limit건 읽는다. */
    List<CommunityImageRow> selectUnattachedCreatedBefore(
            @Param("createdBefore") Instant createdBefore, @Param("limit") int limit);

    /** 호출하면 imageId인 이미지가 아직 글에 붙지 않았을 때만 행을 지우고, 지운 행 수(0 또는 1)를 돌려준다. */
    int deleteIfUnattached(@Param("imageId") long imageId);
}
