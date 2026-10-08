package com.pitchmap.trust.infra;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 완료한 동행과 임박 탈퇴는 베이스캠프의 상태와 멤버 행으로 정해진다. 이 정보를 basecamp 모듈의 클래스로 읽으면 trust가 basecamp에 기대게 된다.
// 그래서 이 매퍼가 basecamp·basecamp_member 테이블을 SQL로 직접 읽고, 결과는 조회 전용 record로만 받는다.
@Mapper
public interface TrustRecordMapper {

    /**
     * 호출하면 memberId인 회원의 동행 기록을 한 번에 센다.
     * <ul>
     *   <li>완료한 동행: 완료된 베이스캠프에서 ACTIVE인 멤버 행(리더 포함, 탈퇴·강퇴 제외)</li>
     *   <li>받은 후기와 "다시 동행" 수: 숨기지 않았고, 완료 시각이 revealCutoff보다 앞이거나 반대 방향 후기가 있는 후기</li>
     *   <li>최근 임박 탈퇴: 임박 탈퇴로 표시됐고 탈퇴 시각이 recentSince 이후(그 시각 포함)인 멤버 행</li>
     *   <li>최근 제재: 시작 시각이 recentSince 이후(그 시각 포함)인 확정 제재가 있는지. 서버가 먼저 내린 임시 정지와 해제된 제재는 넣지 않고,
     *       경고는 넣는다</li>
     * </ul>
     */
    TrustRecordRow selectCompanionRecord(
            @Param("memberId") long memberId,
            @Param("revealCutoff") Instant revealCutoff,
            @Param("recentSince") Instant recentSince);

    /**
     * 호출하면 memberId인 회원이 받은 후기 중 공개된 것의 태그별 횟수를 읽는다. 공개 조건은
     * {@link #selectCompanionRecord}의 받은 후기와 같다. 한 번도 나오지 않은 태그는 행이 없다.
     */
    List<TagCountRow> selectRevealedTagCounts(
            @Param("memberId") long memberId, @Param("revealCutoff") Instant revealCutoff);
}
