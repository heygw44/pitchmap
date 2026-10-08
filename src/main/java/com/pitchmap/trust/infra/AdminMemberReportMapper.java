package com.pitchmap.trust.infra;

import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 신고 목록과 상세에는 신고자·대상 닉네임과 베이스캠프 정보가 함께 필요하다. 이 정보를 basecamp·member 모듈의 클래스로 읽으면
// trust가 그 모듈들에 기대게 된다. 그래서 이 매퍼가 member·basecamp 테이블을 SQL로 직접 읽고, 결과는 조회 전용 record로만 받는다.
@Mapper
public interface AdminMemberReportMapper {

    /**
     * 호출하면 신고를 긴급 신고, 금전 요구 신고, 나머지 순으로, 같은 묶음 안에서는 접수 시각이 이른 순서로 offset건 건너뛰고 최대 limit건 읽는다.
     * status가 null이면 모든 상태를, urgent가 null이면 긴급 여부와 상관없이 읽는다.
     */
    List<AdminMemberReportRow> selectList(
            @Param("status") String status,
            @Param("urgent") Boolean urgent,
            @Param("offset") long offset,
            @Param("limit") int limit);

    /** 호출하면 신고 한 건의 상세를 읽는다. 신고가 없으면 빈 값이다. 회원 신고이면 후기 열은 모두 null이다. */
    Optional<AdminMemberReportDetailRow> selectDetail(@Param("reportId") long reportId);

    /** 호출하면 동행 후기에 붙은 태그 이름을 이름순으로 읽는다. */
    List<String> selectReviewTags(@Param("reviewId") long reviewId);

    /**
     * 호출하면 회원의 제재 이력을 최신순으로 읽는다. 기각된 신고가 만든 임시 정지는 제재 이력에 남기지 않으므로 뺀다.
     */
    List<AdminSanctionHistoryRow> selectSanctionHistory(@Param("memberId") long memberId);
}
