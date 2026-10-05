package com.pitchmap.publicdata.infra;

import com.pitchmap.publicdata.domain.SyncJobType;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 실행 기록 목록은 조건과 페이지를 거는 화면 조회라서, 이 프로젝트의 규칙대로 엔티티를 읽지 않고 MyBatis로 필요한 열만 읽는다.
@Mapper
public interface SyncJobRunMapper {

    /**
     * 호출하면 실행 기록을 최근 것부터 offset개 건너뛰고 최대 limit개 돌려준다. jobType이 null이면 모든 종류를 읽는다.
     */
    List<SyncJobRunRow> selectRuns(
            @Param("jobType") SyncJobType jobType, @Param("offset") long offset, @Param("limit") int limit);
}
