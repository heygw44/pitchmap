package com.pitchmap.review.infra;

import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 후기 목록은 작성자의 닉네임을 함께 보여 줘야 하고 페이지 단위로 읽는다.
// JPA로 읽으면 후기마다 회원을 따로 조회하거나, review 모듈이 member 모듈의 엔티티에 기대야 한다.
// 그래서 이 매퍼가 member를 조인하는 SQL을 직접 쓰고, 결과는 조회 전용 record로만 받는다.
@Mapper
public interface SpotReviewMapper {

    /**
     * 호출하면 spotId인 장소의 후기를 작성 시각이 늦은 순서로 offset건 건너뛰고 최대 limit건 읽는다. 작성 시각이 같으면 후기 ID가 큰 것이 먼저다.
     */
    List<SpotReviewRow> selectBySpot(
            @Param("spotId") long spotId, @Param("offset") long offset, @Param("limit") int limit);

    /** 호출하면 reviewId인 후기 한 건을 읽는다. 후기가 없으면 빈 값이다. */
    Optional<SpotReviewRow> selectById(@Param("reviewId") long reviewId);
}
