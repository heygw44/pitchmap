package com.pitchmap.spot.infra;

import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 장소 상세는 spot에 공공데이터 상세, 박지 상세, 제보한 회원의 닉네임, 박지 확인 수를 한 번에 붙여 읽는다.
// JPA로 읽으면 엔티티마다 조회가 따로 나가고, 회원 닉네임을 얻으려고 spot 모듈이 member 모듈의 엔티티에 기대야 한다.
// 그래서 이 매퍼가 조인과 집계를 SQL 한 문장으로 쓰고, 결과는 조회 전용 record로만 받는다.
@Mapper
public interface SpotDetailMapper {

    /** 호출하면 spotId인 ACTIVE 장소의 상세를 읽는다. 장소가 없거나 ACTIVE가 아니면 빈 값이다. */
    Optional<SpotDetailRow> selectActiveDetail(@Param("spotId") long spotId);
}
