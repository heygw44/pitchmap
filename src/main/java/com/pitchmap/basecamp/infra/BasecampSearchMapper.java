package com.pitchmap.basecamp.infra;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 검색은 공간 함수, 상관 서브쿼리, 요청마다 달라지는 지역·날짜·자리 조건을 한 문장에 담는다. JPA로는 이를 표현하기 어려워서 SQL로 직접 쓴다.
// 장소 이름·유형·좌표는 장소 모듈의 응용 서비스를 거치지 않고 spot 표에서 직접 읽는다. 검색 한 번에 장소 수십 곳의 정보가 필요해서
// 장소마다 서비스를 부르면 쿼리가 그만큼 늘기 때문이다.
// 결과는 조회 전용 record로만 받고, 엔티티는 만들지 않는다.
@Mapper
public interface BasecampSearchMapper {

    /**
     * 호출하면 condition에 맞는 모집 중 베이스캠프를 출발일 순서로 offset건 건너뛰고 최대 limit건 읽는다. 출발일이 같으면 베이스캠프 ID 순서다.
     * 지도에 보이는(ACTIVE) 장소의 베이스캠프만 찾는다.
     */
    List<BasecampSearchRow> selectRecruiting(
            @Param("condition") BasecampSearchCondition condition,
            @Param("offset") long offset,
            @Param("limit") int limit);

    /**
     * 호출하면 viewerId인 회원이 basecampIds 가운데 멤버 행이나 신청 행을 남긴 베이스캠프의 상태를 읽는다. 둘 다 없는 베이스캠프는 결과에 없다.
     * basecampIds는 비어 있으면 안 된다.
     */
    List<ViewerHistoryRow> selectViewerHistory(
            @Param("viewerId") long viewerId, @Param("basecampIds") List<Long> basecampIds);

    /** 호출하면 viewerId인 회원이 ACTIVE 멤버로 들어 있는 확정된 베이스캠프의 일정을 읽는다. */
    List<ConfirmedScheduleRow> selectConfirmedSchedules(@Param("viewerId") long viewerId);

    /** 호출하면 spotId인 장소의 이름과 유형을 읽는다. 장소의 상태는 거르지 않고, 없으면 null이다. */
    SpotSummaryRow selectSpotSummary(@Param("spotId") long spotId);
}
