package com.pitchmap.spot.infra;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 장소 상세는 spot에 공공데이터 상세, 박지 상세, 제보한 회원의 닉네임, 박지 확인 수를 한 번에 붙여 읽는다.
// 평균 평점, 후기 수, 최근 후기는 저장하지 않고 spot_review 행에서 조회할 때 계산한다.
// JPA로 읽으면 엔티티마다 조회가 따로 나가고, 회원 닉네임을 얻으려고 spot 모듈이 member 모듈의 엔티티에 기대야 한다.
// 그래서 이 매퍼가 조인과 집계를 SQL 한 문장으로 쓰고, 결과는 조회 전용 record로만 받는다.
// 예상 인원과 모집 중인 베이스캠프도 이 매퍼가 basecamp와 basecamp_member 테이블을 직접 읽는다.
// basecamp 모듈이 spot 모듈을 부르는 방향이라 spot은 basecamp의 서비스를 호출할 수 없다. 하지만 베이스캠프 정보를 이벤트로 받아 따로 저장하면
// 같은 사실이 두 테이블에 남는다. 그래서 spot_review처럼 SQL만 테이블을 읽고, Java 클래스는 basecamp 모듈을 참조하지 않는다.
// 예상 인원에 더하는 공식 행사 참가자도 같은 이유로 program과 program_application 테이블을 SQL로만 읽는다.
@Mapper
public interface SpotDetailMapper {

    /** 호출하면 spotId인 ACTIVE 장소의 상세를 읽는다. 장소가 없거나 ACTIVE가 아니면 빈 값이다. */
    Optional<SpotDetailRow> selectActiveDetail(@Param("spotId") long spotId);

    /** 호출하면 spotId인 장소의 후기 수와 평균 평점을 계산한다. 후기가 없으면 후기 수 0과 {@code null} 평균을 돌려준다. */
    SpotRatingRow selectRating(@Param("spotId") long spotId);

    /** 호출하면 spotId인 장소의 후기를 작성 시각이 늦은 순서로 최대 limit건 읽는다. 작성 시각이 같으면 후기 ID가 큰 것이 먼저다. */
    List<SpotRecentReviewRow> selectRecentReviews(@Param("spotId") long spotId, @Param("limit") int limit);

    /**
     * 호출하면 spotId인 장소에서 확정된 베이스캠프 중 종료일이 today보다 늦은 것의 야영 기간과 ACTIVE 멤버 수를 읽는다. 캠프 리더도 센다. 밤별로 펼쳐
     * 합산하는 일은 호출하는 쪽이 한다.
     */
    List<SpotConfirmedStayRow> selectConfirmedStays(@Param("spotId") long spotId, @Param("today") LocalDate today);

    /**
     * 호출하면 spotId인 장소에 연결된 숙박 공식 행사 중 취소되지 않았고 종료 시각이 from보다 늦은 것의 시작·종료 시각과 확정된 참가 신청 수를
     * 읽는다. 확정 신청이 없는 행사는 빼지 않고 0명으로 읽는다. 밤별로 펼쳐 합산하는 일은 호출하는 쪽이 한다.
     */
    List<SpotConfirmedProgramRow> selectConfirmedPrograms(@Param("spotId") long spotId, @Param("from") Instant from);

    /** 호출하면 spotId인 장소에서 모집 중인 베이스캠프를 출발일이 빠른 순서로 읽는다. 출발일이 같으면 ID가 작은 것이 먼저다. */
    List<SpotRecruitingBasecampRow> selectRecruitingBasecamps(@Param("spotId") long spotId);
}
