package com.pitchmap.trust.infra;

import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 신고 자격은 신고자와 대상이 그 베이스캠프에 신청했거나 멤버였는지로 정해진다. 이 정보를 basecamp 모듈의 클래스로 읽으면
// trust가 basecamp에 기대게 된다. 그래서 이 매퍼가 basecamp 테이블을 SQL로 직접 읽고, 결과는 조회 전용 record로만 받는다.
@Mapper
public interface MemberReportMapper {

    /**
     * 호출하면 신고 자격을 가리는 데 필요한 값을 한 번에 읽는다. 베이스캠프가 없으면 빈 값이다.
     * 신고자와 대상이 그 베이스캠프에 신청 행이나 멤버 행을 가졌는지는 상태와 무관하게 읽는다. 캠프 리더는 멤버 행으로만 남는다.
     * 같은 신고를 이미 했는지도 함께 읽는다. companionReviewId가 null이면 후기 조건은 모두 false다.
     */
    Optional<MemberReportContextRow> selectContext(
            @Param("basecampId") long basecampId,
            @Param("reporterId") long reporterId,
            @Param("targetId") long targetId,
            @Param("kind") String kind,
            @Param("companionReviewId") Long companionReviewId);
}
