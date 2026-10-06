package com.pitchmap.spot.infra;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 중복 후보 조회는 공간 인덱스를 타는 후보 사각형 조건과 구면 거리 조건을 함께 걸어 가까운 순서로 읽는다.
// JPA로는 공간 함수를 쓰는 조회를 표현하기 어려워서, 이 매퍼가 SQL을 직접 쓴다. 결과는 조회 전용 record로만 받는다.
// 이 매퍼의 메서드는 호출하는 쪽이 연 트랜잭션 안에서 실행해야 한다.
@Mapper
public interface BakjiDuplicateMapper {

    /**
     * 호출하면 condition 반경 안의 ACTIVE 박지를 가까운 순서로 최대 condition.limit건 읽는다. 거리가 같으면 장소 ID 순서다.
     * condition.excludeSpotId인 장소는 뺀다.
     */
    List<BakjiDuplicateRow> selectDuplicates(@Param("dup") BakjiDuplicateCondition condition);
}
