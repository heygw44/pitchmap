package com.pitchmap.spot.infra;

import com.pitchmap.spot.domain.BakjiReport;
import com.pitchmap.spot.domain.BakjiReportRepository;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 도메인 리포지토리와 JpaRepository가 같은 이름의 메서드를 따로 선언해서, 구현체 타입으로 호출하면 모호해진다.
// 그래서 우리는 이 인터페이스에서 메서드를 다시 선언해 하나로 합친다.
public interface BakjiReportJpaRepository extends JpaRepository<BakjiReport, Long>, BakjiReportRepository {

    @Override
    BakjiReport saveAndFlush(BakjiReport report);

    // flushAutomatically만 켠다. clearAutomatically를 켜면 아직 플러시하지 않은 장소 상태 변경까지 영속성 컨텍스트에서 버린다.
    @Override
    @Modifying(flushAutomatically = true)
    @Query("UPDATE BakjiReport r SET r.reviewedAt = :now WHERE r.spotId = :spotId AND r.reviewedAt IS NULL")
    int markReviewed(@Param("spotId") long spotId, @Param("now") Instant now);
}
