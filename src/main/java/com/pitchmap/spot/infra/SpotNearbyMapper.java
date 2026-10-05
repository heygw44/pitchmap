package com.pitchmap.spot.infra;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 반경 검색은 공간 인덱스를 타는 후보 사각형 조건과 구면 거리 조건에 유형, 시설, 경고 필터를 요청마다 골라 붙이고 거리순으로 정렬한다.
// JPA로는 공간 함수와 동적 조건을 한 문장에 담기 어렵다. 그래서 이 매퍼가 SQL로 직접 쓴다.
// 결과는 조회 전용 record로만 받고, 엔티티는 만들지 않는다.
@Mapper
public interface SpotNearbyMapper {

    /**
     * 호출하면 nearby 반경 안의 ACTIVE 장소를 가까운 순서로 offset건 건너뛰고 최대 limit건 읽는다. 거리가 같으면 장소 ID 순서다. 공공데이터
     * 장소는 운영 상태와 휴장 기간을 함께 읽고, 박지는 그 값들이 {@code null}이다.
     */
    List<SpotNearbyRow> selectNearby(
            @Param("nearby") SpotNearbyCondition nearby, @Param("offset") long offset, @Param("limit") int limit);
}
