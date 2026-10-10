package com.pitchmap.community.infra;

import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 글 목록은 작성자의 닉네임과 연결한 장소의 이름을 함께 보여 주고, 조건이 선택적이며 페이지 단위로 읽는다.
// JPA로 읽으면 글마다 회원과 장소를 따로 조회하거나, community 모듈이 member·spot 모듈의 엔티티에 기대야 한다.
// 그래서 이 매퍼가 member와 spot을 조인하는 SQL을 직접 쓰고, 결과는 조회 전용 record로만 받는다.
@Mapper
public interface CommunityPostMapper {

    /**
     * 호출하면 ACTIVE인 글을 최신순(글 ID 내림차순)으로 offset건 건너뛰고 최대 limit건 읽는다. text에는 본문 앞 100자만 담는다.
     * spotId는 null이면 조건에서 뺀다.
     */
    List<CommunityPostRow> selectActive(
            @Param("spotId") Long spotId, @Param("offset") long offset, @Param("limit") int limit);

    /** 호출하면 {@link #selectActive}와 같은 조건에 맞는 글의 수를 센다. spotId는 null이면 조건에서 뺀다. */
    long countActive(@Param("spotId") Long spotId);

    /**
     * 호출하면 ACTIVE인 글 한 건을 본문 전체와 함께 읽는다. 글이 없거나 ACTIVE가 아니면 빈 값이다.
     * viewerId가 null이면 likedByMe를 읽지 않고 null로 둔다.
     */
    Optional<CommunityPostRow> selectActiveById(@Param("postId") long postId, @Param("viewerId") Long viewerId);
}
