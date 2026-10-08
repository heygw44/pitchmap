package com.pitchmap.basecamp.infra;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 제재를 받은 회원이 걸친 베이스캠프를 모두 찾는 조회다. 엔티티로는 회원에서 베이스캠프로 가는 길이 없어서
// (멤버와 신청은 베이스캠프가 소유한다) ID만 SQL로 읽고, 실제 변경은 베이스캠프 엔티티를 잠그고 고친다.
@Mapper
public interface BasecampSanctionCleanupMapper {

    /**
     * 호출하면 memberId인 회원이 ACTIVE 멤버이거나 대기 중인 신청자인 진행 중(모집 중, 마감, 확정) 베이스캠프의 ID를 오름차순으로 읽는다.
     * 캠프 리더도 ACTIVE 멤버 행이 있으므로 포함된다.
     */
    List<Long> selectInProgressBasecampIds(@Param("memberId") long memberId);
}
