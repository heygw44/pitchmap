package com.pitchmap.spot.infra;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 공원 경계 판정은 MySQL의 공간 함수 ST_Contains로 점이 경계 안에 있는지 계산한다. JPA로는 이 함수를 쓰는 조회와
// 판정 결과의 일괄 갱신을 표현하기 어려워서, 이 매퍼가 SQL을 직접 쓴다.
// 이 매퍼의 메서드는 호출하는 쪽(ParkAreaJudgeService)이 연 트랜잭션 안에서 실행해야 한다.
@Mapper
public interface ParkAreaJudgeMapper {

    /** 호출하면 좌표를 포함하는 공원 경계의 ID를 돌려준다. 여러 경계가 겹치면 가장 작은 ID를, 포함하는 경계가 없으면 null을 돌려준다. */
    Long selectContainingAreaId(@Param("lat") double latitude, @Param("lng") double longitude);

    /** 호출하면 id인 공원 경계의 이름을 돌려준다. 그런 경계가 없으면 null을 돌려준다. */
    String selectAreaName(@Param("id") long id);

    /** 호출하면 상태와 상관없이 모든 박지의 ID와 좌표를 ID 순서로 돌려준다. */
    List<BakjiPointRow> selectBakjiPoints();

    /**
     * 호출하면 spotIds의 박지에 판정 결과를 기록한다. protectedAreaId가 null이면 경계 밖으로 보고 경고를 끄며, 아니면 경고를 켜고 그 경계를 근거로 남긴다.
     * area_checked_at은 now로 기록하고 updated_at은 건드리지 않는다. spotIds는 비어 있으면 안 된다. 갱신한 행 수를 돌려준다.
     */
    int updateJudgement(
            @Param("spotIds") List<Long> spotIds,
            @Param("protectedAreaId") Long protectedAreaId,
            @Param("now") Instant now);
}
