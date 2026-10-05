package com.pitchmap.spot.infra;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 공공데이터 동기화는 원천 API 한 페이지(수십~수백 건)를 한 번에 적재한다. JPA로 저장하면 행마다 INSERT나 UPDATE가 따로 나가고,
// 기존 행을 엔티티로 읽어 영속성 컨텍스트에 올리는 비용도 든다. 그래서 이 매퍼는 기존 행 조회, 새 행 추가, 바뀐 행 갱신을
// 테이블마다 SQL 한 문장으로 처리한다. 이 매퍼의 메서드는 호출하는 쪽(PublicSpotSyncService)이 연 트랜잭션 안에서 실행해야 하고,
// 목록 인자가 비어 있으면 호출하지 않는다.
@Mapper
public interface PublicSpotMapper {

    /** 호출하면 source 출처에서 externalIds에 해당하는 장소를 spot과 public_spot_detail을 묶어 읽는다. */
    List<PublicSpotRow> selectBySourceAndExternalIds(
            @Param("source") String source, @Param("externalIds") Collection<String> externalIds);

    /** 호출하면 spot에 ACTIVE 상태로 새 행을 추가하고, DB가 만든 ID를 각 행의 spotId에 채운다. */
    int insertSpots(
            @Param("spotType") String spotType,
            @Param("spots") List<PublicSpotInsertRow> spots,
            @Param("now") Instant now);

    /** 호출하면 insertSpots로 ID를 받은 행마다 public_spot_detail을 추가한다. */
    int insertDetails(
            @Param("source") String source, @Param("spots") List<PublicSpotInsertRow> spots, @Param("now") Instant now);

    /**
     * 호출하면 spot의 이름, 주소, 좌표, 기상청 격자를 새 값으로 바꾼다. 상태, 공원 경고, 생성 시각은 건드리지 않는다. 그래서 관리자가 숨긴 장소는
     * 동기화 뒤에도 숨겨진 채로 남는다.
     */
    int updateSpots(@Param("spots") List<PublicSpotUpdateRow> spots, @Param("now") Instant now);

    /** 호출하면 public_spot_detail의 분류, 시설, 전화번호, 홈페이지를 새 값으로 바꾸고 synced_at을 now로 기록한다. */
    int updateDetails(@Param("spots") List<PublicSpotUpdateRow> spots, @Param("now") Instant now);
}
