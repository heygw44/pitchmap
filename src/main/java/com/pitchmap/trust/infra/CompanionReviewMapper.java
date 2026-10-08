package com.pitchmap.trust.infra;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 후기를 쓸 자격은 베이스캠프의 상태와 멤버 목록으로 정해지고, 목록에는 베이스캠프 제목과 상대의 닉네임이 함께 필요하다.
// 이 정보를 basecamp 모듈의 클래스로 읽으면 trust가 basecamp에 기대게 된다. 그래서 이 매퍼가 basecamp·member 테이블을 SQL로 직접 읽고,
// 결과는 조회 전용 record로만 받는다.
@Mapper
public interface CompanionReviewMapper {

    /**
     * 호출하면 후기를 쓸 자격을 가리는 데 필요한 값을 한 번에 읽는다. 베이스캠프가 없으면 빈 값이다.
     * 작성자와 상대가 그 베이스캠프의 ACTIVE 멤버인지, 같은 상대에게 이미 썼는지도 함께 읽는다.
     */
    Optional<CompanionReviewWriteContextRow> selectWriteContext(
            @Param("basecampId") long basecampId,
            @Param("reviewerId") long reviewerId,
            @Param("revieweeId") long revieweeId);

    /**
     * 호출하면 memberId인 회원이 ACTIVE 멤버인 완료 베이스캠프 중, 완료 시각이 writableSince 이후(그 시각 포함)인 것마다
     * 아직 후기를 쓰지 않은 다른 ACTIVE 멤버를 한 행씩 읽는다. 대상이 없는 베이스캠프는 나오지 않는다.
     * 완료 시각이 빠른(마감이 빠른) 베이스캠프부터, 같은 베이스캠프 안에서는 대상 회원 ID 순이다.
     */
    List<CompanionReviewPendingRow> selectPending(
            @Param("memberId") long memberId, @Param("writableSince") Instant writableSince);

    /**
     * 호출하면 memberId인 회원이 받은 후기 중 숨기지 않은 것을 작성 시각이 늦은 순서로 offset건 건너뛰고 최대 limit건 읽는다.
     * 작성 시각이 같으면 후기 ID가 큰 것이 먼저다. 공개 여부는 읽지 않고, 반대 방향 후기가 있는지만 함께 읽는다.
     */
    List<CompanionReviewReceivedRow> selectReceived(
            @Param("memberId") long memberId, @Param("offset") long offset, @Param("limit") int limit);

    /**
     * 호출하면 memberId인 회원이 받은 후기 중 다른 회원에게 보일 것을 최신순으로 offset건 건너뛰고 최대 limit건 읽는다.
     * 숨긴 후기와, 반대 방향 후기가 없고 완료 시각이 revealCutoff 이후인(작성 기한이 남은) 후기는 뺀다.
     */
    List<CompanionReviewPublicRow> selectPublicByReviewee(
            @Param("memberId") long memberId,
            @Param("revealCutoff") Instant revealCutoff,
            @Param("offset") long offset,
            @Param("limit") int limit);
}
