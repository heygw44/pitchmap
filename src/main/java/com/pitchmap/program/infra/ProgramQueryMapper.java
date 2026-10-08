package com.pitchmap.program.infra;

import com.pitchmap.program.domain.ProgramApplicationStatus;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 목록은 남은 자리를 신청 테이블의 상관 서브쿼리로 세고, 신청자 목록은 회원 테이블의 닉네임을 조인한다. JPA로는 이를 표현하기 어려워서 SQL로 직접 쓴다.
// 장소와 회원 테이블은 다른 모듈의 서비스를 거치지 않고 직접 읽는다. 목록 한 페이지에 필요한 정보를 한 번에 가져오려는 것이다.
// 결과는 조회 전용 record로만 받고, 엔티티는 만들지 않는다.
@Mapper
public interface ProgramQueryMapper {

    /**
     * 호출하면 취소되지 않은 행사 중 phase인 것을 행사 시작 시각이 이른 순서로 offset건 건너뛰고 최대 limit건 읽는다. 시작 시각이 같으면 행사 ID 순서다.
     * phase는 UPCOMING, OPEN, CLOSED 중 하나이고 null이면 단계로 거르지 않는다. 단계는 now를 신청 시작·마감 시각과 비교해 정한다.
     */
    List<ProgramListRow> selectList(
            @Param("phase") String phase,
            @Param("now") Instant now,
            @Param("offset") long offset,
            @Param("limit") int limit);

    /** 호출하면 programId인 행사를 취소 여부와 상관없이 읽는다. 없으면 null이다. */
    ProgramDetailRow selectDetail(@Param("programId") long programId);

    /** 호출하면 memberId인 회원이 programId인 행사에 낸 신청 중 가장 최근 신청을 읽는다. 없으면 null이다. */
    MyApplicationRow selectLatestApplication(@Param("programId") long programId, @Param("memberId") long memberId);

    /**
     * 호출하면 programId인 행사의 신청 중 status인 것을 신청 ID 순서로 offset건 건너뛰고 최대 limit건 읽는다. status가 null이면 상태로 거르지 않는다.
     */
    List<ProgramApplicantRow> selectApplicants(
            @Param("programId") long programId,
            @Param("status") ProgramApplicationStatus status,
            @Param("offset") long offset,
            @Param("limit") int limit);

    /** 호출하면 spotId인 장소가 있고 지도에 보이는(ACTIVE) 상태이면 true를 돌려준다. */
    boolean existsActiveSpot(@Param("spotId") long spotId);
}
