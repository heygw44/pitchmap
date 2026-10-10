package com.pitchmap.community.infra;

import com.pitchmap.community.domain.CommunityReport;
import com.pitchmap.community.domain.CommunityReportRepository;
import com.pitchmap.community.domain.CommunityReportTargetType;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 도메인 리포지토리와 JpaRepository가 같은 이름의 메서드를 따로 선언해서, 구현체 타입으로 호출하면 모호해진다.
// 그래서 우리는 이 인터페이스에서 메서드를 다시 선언해 하나로 합친다.
public interface CommunityReportJpaRepository extends JpaRepository<CommunityReport, Long>, CommunityReportRepository {

    @Override
    CommunityReport saveAndFlush(CommunityReport report);

    // flushAutomatically만 켠다. clearAutomatically를 켜면 아직 플러시하지 않은 글이나 댓글의 상태 변경까지 영속성 컨텍스트에서 버린다.
    @Override
    @Modifying(flushAutomatically = true)
    @Query("UPDATE CommunityReport r SET r.reviewedAt = :now "
            + "WHERE r.targetType = :targetType AND r.targetId = :targetId AND r.reviewedAt IS NULL")
    int markReviewed(
            @Param("targetType") CommunityReportTargetType targetType,
            @Param("targetId") long targetId,
            @Param("now") Instant now);
}
