package com.pitchmap.spot.infra;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 지도 영역 조회는 공간 인덱스를 타는 영역 조건에 유형, 시설, 경고 필터를 요청마다 골라 붙이고, 장소가 많으면 격자 칸별로 묶어 센다.
// JPA로는 공간 함수와 동적 조건, 집계를 한 문장에 담기 어렵다. 그래서 이 매퍼가 SQL로 직접 쓴다.
// 결과는 조회 전용 record로만 받고, 엔티티는 만들지 않는다.
@Mapper
public interface SpotAreaMapper {

    /**
     * 호출하면 area 안의 ACTIVE 장소를 ID 순서로 최대 limit건 읽는다. 공공데이터 장소는 운영 상태와 휴장 기간을 함께 읽고, 박지는 그 값들이
     * {@code null}이다.
     */
    List<SpotMarkerRow> selectMarkers(@Param("area") SpotAreaCondition area, @Param("limit") int limit);

    /**
     * 호출하면 {@link #selectMarkers}와 같은 장소를 grid 칸별로 묶어 칸마다 중심 좌표와 장소 수를 읽는다. 장소가 없는 칸은 결과에 나오지 않고, 결과는
     * 남쪽 행부터, 같은 행에서는 서쪽 칸부터 나온다.
     */
    List<SpotClusterRow> selectClusters(@Param("area") SpotAreaCondition area, @Param("grid") SpotClusterGrid grid);
}
