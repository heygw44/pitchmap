package com.pitchmap.basecamp.infra;

import com.pitchmap.basecamp.domain.BasecampRelation;
import com.pitchmap.basecamp.domain.BasecampStatus;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 멤버 표와 신청 표를 합쳐서 요청자와 관계 있는 베이스캠프를 찾고, 장소 이름·유형까지 한 번에 읽는다. 둘 이상의 표를 합치는 조회라서 SQL로 직접 쓴다.
// 결과는 조회 전용 record로만 받고, 엔티티는 만들지 않는다.
@Mapper
public interface MyBasecampMapper {

    /**
     * 호출하면 memberId인 회원이 지금 ACTIVE 멤버(캠프 리더 또는 멤버)이거나 PENDING 신청자인 베이스캠프를 출발일이 늦은 순서로 offset건
     * 건너뛰고 최대 limit건 읽는다. 출발일이 같으면 베이스캠프 ID가 큰 순서다. relation이나 status가 null이면 그 조건으로는 거르지 않는다.
     * 장소의 상태로는 거르지 않는다.
     */
    List<MyBasecampRow> selectMine(
            @Param("memberId") long memberId,
            @Param("relation") BasecampRelation relation,
            @Param("status") BasecampStatus status,
            @Param("offset") long offset,
            @Param("limit") int limit);
}
