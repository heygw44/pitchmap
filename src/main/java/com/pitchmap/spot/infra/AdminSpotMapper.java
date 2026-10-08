package com.pitchmap.spot.infra;

import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 관리자 검토 목록은 장소, 제보자 닉네임, 신고 사유별 집계, 장소마다 최근 신고 5건을 한 화면에 모은다. JPA 엔티티로는 이 조인과 집계,
// 장소별 상위 N건(윈도 함수)을 한 문장에 담기 어렵다. 그래서 이 매퍼가 SQL로 직접 읽고, 결과는 조회 전용 record로만 받는다.
@Mapper
public interface AdminSpotMapper {

    /**
     * 호출하면 status인 장소를 상태가 바뀐 시각이 이른 순서(같으면 ID 순서)로 offset건 건너뛰고 최대 limit건 읽는다. 신고 수는 검토 전
     * 신고만 센다.
     */
    List<AdminSpotRow> selectPage(
            @Param("status") String status, @Param("offset") long offset, @Param("limit") int limit);

    /** 호출하면 spotIds 장소마다 검토 전 신고를 최신순으로 최대 5건씩 읽는다. spotIds가 비어 있으면 호출하지 않는다. */
    List<AdminSpotRecentReportRow> selectRecentReports(@Param("spotIds") Collection<Long> spotIds);
}
