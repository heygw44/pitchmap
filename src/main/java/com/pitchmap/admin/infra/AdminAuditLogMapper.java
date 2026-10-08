package com.pitchmap.admin.infra;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 감사 로그 목록은 조건과 페이지를 거는 화면 조회라서, 이 프로젝트의 규칙대로 엔티티를 읽지 않고 MyBatis로 필요한 열만 읽는다.
@Mapper
public interface AdminAuditLogMapper {

    /**
     * 호출하면 감사 로그를 id가 큰 것(최근 기록)부터 offset건 건너뛰고 최대 limit건 읽는다.
     * adminId, targetType, from, to가 null이 아니면 그 조건으로 거른다. 기간은 from 이상 to 미만이다.
     */
    List<AdminAuditLogRow> selectLogs(
            @Param("adminId") Long adminId,
            @Param("targetType") String targetType,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("offset") long offset,
            @Param("limit") int limit);
}
