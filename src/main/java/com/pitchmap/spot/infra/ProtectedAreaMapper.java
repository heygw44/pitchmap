package com.pitchmap.spot.infra;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// protected_area는 서버가 공원 경계 파일을 적재할 때만 쓰는 테이블이고, 화면에서 한 건씩 고치는 일이 없다. 그래서 엔티티를 두지 않고 처음부터 MyBatis로 쓴다.
// 경계 WKT는 MySQL의 ST_GeomFromText로 바꿔 넣는다. 이 매퍼의 메서드는 호출하는 쪽(ProtectedAreaLoadService)이 연 트랜잭션 안에서 실행해야 한다.
@Mapper
public interface ProtectedAreaMapper {

    /** 호출하면 source 출처의 경계를 모두 읽어, 각 경계의 ID, 구분, 이름을 돌려준다. 경계 도형은 읽지 않는다. */
    List<ProtectedAreaKeyRow> selectKeysBySource(@Param("source") String source);

    /** 호출하면 경계 하나를 추가하고 created_at과 updated_at을 now로 기록한다. */
    int insert(@Param("area") ProtectedAreaInsertRow area, @Param("now") Instant now);

    /** 호출하면 ID로 찾은 경계 하나의 도형과 기준일을 새 값으로 바꾸고 updated_at을 now로 기록한다. 이름, 구분, 출처, 생성 시각은 건드리지 않는다. */
    int updateBoundary(@Param("area") ProtectedAreaUpdateRow area, @Param("now") Instant now);
}
