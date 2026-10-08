package com.pitchmap.basecamp.infra;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 자동 처리는 여러 인스턴스나 캠프 리더의 직접 조작과 겹쳐도 한 번만 일어나야 한다. 그래서 엔티티를 읽고 고치지 않고,
// 상태 조건을 WHERE에 건 UPDATE를 직접 쓴다. 바뀐 열은 도메인 엔티티의 같은 전이가 바꾸는 열과 같아야 한다.
@Mapper
public interface BasecampAutoTransitionMapper {

    /** 호출하면 모집 중이거나 마감된 베이스캠프 가운데 출발일이 dueStartDate 이하인 ID를 afterId 뒤부터 오름차순으로 최대 limit개 읽는다. */
    List<Long> selectDueForDepartureIds(
            @Param("dueStartDate") LocalDate dueStartDate, @Param("afterId") long afterId, @Param("limit") int limit);

    /** 호출하면 확정된 베이스캠프 가운데 종료일이 today보다 앞서는 ID를 afterId 뒤부터 오름차순으로 최대 limit개 읽는다. */
    List<Long> selectDueForCompletionIds(
            @Param("today") LocalDate today, @Param("afterId") long afterId, @Param("limit") int limit);

    /** 호출하면 베이스캠프 행을 쓰기 잠금으로 읽어 상태를 돌려준다. 행이 없으면 null이다. */
    String selectStatusForUpdate(@Param("id") long id);

    int countActiveMembers(@Param("id") long id);

    /** 호출하면 ACTIVE 멤버의 회원 ID를 합류한 순서대로 돌려준다. 캠프 리더가 맨 앞이다. */
    List<Long> selectActiveMemberIds(@Param("id") long id);

    /** 호출하면 모집 중이거나 마감된 베이스캠프를 확정으로 바꾸고 바뀐 행 수를 돌려준다. */
    int confirmIfBeforeConfirm(@Param("id") long id, @Param("now") Instant now);

    /** 호출하면 모집 중이거나 마감된 베이스캠프를 인원 부족으로 취소하고 바뀐 행 수를 돌려준다. */
    int cancelForNotEnoughMembers(@Param("id") long id, @Param("now") Instant now);

    /** 호출하면 종료일이 today보다 앞선 확정 베이스캠프를 완료로 바꾸고 바뀐 행 수를 돌려준다. */
    int completeIfConfirmed(@Param("id") long id, @Param("today") LocalDate today, @Param("now") Instant now);

    /** 호출하면 결정되지 않은 합류 신청을 모두 만료로 바꾸고 바뀐 행 수를 돌려준다. */
    int expirePendingApplications(@Param("basecampId") long basecampId, @Param("now") Instant now);
}
