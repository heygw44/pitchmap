package com.pitchmap.basecamp.infra;

import com.pitchmap.basecamp.domain.BasecampApplicationStatus;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 신청 목록은 상태 조건과 페이지를 한 문장으로 읽는 조회 전용이라 SQL로 직접 쓴다. 결과는 조회 전용 record로만 받고, 엔티티는 만들지 않는다.
@Mapper
public interface BasecampApplicationMapper {

    /**
     * 호출하면 basecampId인 베이스캠프의 신청 가운데 status인 것을 신청이 오래된 순서로 offset건 건너뛰고 최대 limit건 읽는다.
     * 신청 시각이 같으면 신청 ID 순서다.
     */
    List<BasecampApplicationRow> selectByStatus(
            @Param("basecampId") long basecampId,
            @Param("status") BasecampApplicationStatus status,
            @Param("offset") long offset,
            @Param("limit") int limit);
}
