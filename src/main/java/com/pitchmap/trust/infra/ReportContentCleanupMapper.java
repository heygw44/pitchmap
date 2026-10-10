package com.pitchmap.trust.infra;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 컬럼 값만 NULL로 바꾸고 행은 남기는 일괄 갱신이고 갱신할 행 수를 제한해야 해서 JPA 대신 MyBatis를 쓴다.
@Mapper
public interface ReportContentCleanupMapper {

    /**
     * 호출하면 처리 완료(조치·기각)한 시각이 cutoff와 같거나 이른 신고의 내용(content)과 처리 메모(result_note)를 최대 limit건 NULL로
     * 바꾸고, 바뀐 행 수를 돌려준다. 접수·검토 중인 신고는 바꾸지 않는다. 내용을 이미 지운 행은 대상이 아니다.
     */
    int clearHandledReportContent(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    /**
     * 호출하면 처리가 끝난 지 오래된 제재의 사유(reason)를 최대 limit건 NULL로 바꾸고, 바뀐 행 수를 돌려준다.
     * 기간이 끝난 정지는 종료 시각, 해제한 제재는 해제 시각, 경고는 시작 시각이 cutoff와 같거나 이른 것이 대상이다.
     * 영구 정지와 적용 중인 정지는 끝나지 않았으므로 바꾸지 않는다. 사유를 이미 지운 행은 대상이 아니다.
     */
    int clearFinishedSanctionReason(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
