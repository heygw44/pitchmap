package com.pitchmap.trust.infra;

import com.pitchmap.trust.domain.Sanction;
import com.pitchmap.trust.domain.SanctionRepository;
import com.pitchmap.trust.domain.SanctionStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 도메인 리포지토리와 JpaRepository가 같은 이름의 메서드를 따로 선언해서, 구현체 타입으로 호출하면 모호해진다.
// 그래서 우리는 이 인터페이스에서 메서드를 다시 선언해 하나로 합친다.
public interface SanctionJpaRepository extends JpaRepository<Sanction, Long>, SanctionRepository {

    @Override
    Sanction save(Sanction sanction);

    @Override
    @Query("""
            select max(s.level) from Sanction s
            where s.memberId = :memberId
              and s.type <> com.pitchmap.trust.domain.SanctionType.TEMPORARY_72H
              and s.status <> com.pitchmap.trust.domain.SanctionStatus.LIFTED
            """)
    Optional<Byte> findHighestConfirmedLevel(@Param("memberId") long memberId);

    @Override
    @Query("""
            select count(s) > 0 from Sanction s
            where s.memberId = :memberId
              and s.status = com.pitchmap.trust.domain.SanctionStatus.ACTIVE
              and s.type <> com.pitchmap.trust.domain.SanctionType.WARNING
              and (s.endsAt is null or s.endsAt > :now)
            """)
    boolean existsActiveSuspension(@Param("memberId") long memberId, @Param("now") Instant now);

    @Override
    boolean existsByMemberIdAndStatusNot(long memberId, SanctionStatus status);

    @Override
    Optional<Sanction> findById(Long id);

    @Override
    @Query("SELECT s.memberId FROM Sanction s WHERE s.id = :sanctionId")
    Optional<Long> findMemberIdById(@Param("sanctionId") long sanctionId);

    // 이름만으로는 Spring Data가 쿼리를 만들 수 없어서 JPQL을 직접 쓴다.
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Sanction s WHERE s.id = :id")
    Optional<Sanction> findByIdForUpdate(@Param("id") long id);

    @Override
    @Query("""
            select s from Sanction s
            where s.memberId = :memberId
              and s.status = com.pitchmap.trust.domain.SanctionStatus.ACTIVE
              and s.type <> com.pitchmap.trust.domain.SanctionType.WARNING
            """)
    List<Sanction> findActiveSuspensions(@Param("memberId") long memberId);

    @Override
    @Query("""
            select s from Sanction s
            where s.reportId = :reportId
              and s.type = com.pitchmap.trust.domain.SanctionType.TEMPORARY_72H
              and s.status = com.pitchmap.trust.domain.SanctionStatus.ACTIVE
            """)
    List<Sanction> findActiveTemporaryByReportId(@Param("reportId") long reportId);
}
