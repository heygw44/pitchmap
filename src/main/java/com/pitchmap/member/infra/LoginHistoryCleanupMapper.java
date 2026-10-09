package com.pitchmap.member.infra;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 지울 행 수를 제한하는 삭제 문장이라 JPA 대신 MyBatis를 쓴다.
@Mapper
public interface LoginHistoryCleanupMapper {

    /** 호출하면 만든 시각이 cutoff보다 먼저인 로그인 기록을 최대 limit개 지우고, 지운 행 수를 돌려준다. */
    int deleteOlderThan(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
