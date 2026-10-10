package com.pitchmap.program.infra;

import com.pitchmap.program.domain.ProgramVacancyAlert;
import com.pitchmap.program.domain.ProgramVacancyAlertRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProgramVacancyAlertJpaRepository
        extends JpaRepository<ProgramVacancyAlert, Long>, ProgramVacancyAlertRepository {

    // 같은 회원이 동시에 두 번 신청해도 유니크 제약 위반 예외가 나지 않게 DB가 중복을 무시한다. 영향 행 수로 새로 만들었는지 가린다.
    @Override
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT IGNORE INTO program_vacancy_alert (program_id, member_id, created_at)
            VALUES (:programId, :memberId, :now)
            """)
    int subscribe(@Param("programId") long programId, @Param("memberId") long memberId, @Param("now") Instant now);

    @Override
    @Modifying
    @Query("delete from ProgramVacancyAlert v where v.programId = :programId and v.memberId = :memberId")
    void unsubscribe(@Param("programId") long programId, @Param("memberId") long memberId);

    @Override
    @Query("""
            select v.memberId from ProgramVacancyAlert v
            where v.programId = :programId
              and not exists (
                  select 1 from ProgramApplication a
                  where a.programId = v.programId
                    and a.memberId = v.memberId
                    and a.status in (
                        com.pitchmap.program.domain.ProgramApplicationStatus.PENDING_PAYMENT,
                        com.pitchmap.program.domain.ProgramApplicationStatus.CONFIRMED))
            order by v.memberId
            """)
    List<Long> findAlertTargets(@Param("programId") long programId);

    @Override
    @Modifying
    @Query("""
            update ProgramVacancyAlert v
            set v.notifiedAt = :now
            where v.programId = :programId
              and v.memberId in :memberIds
            """)
    void markNotified(
            @Param("programId") long programId,
            @Param("memberIds") Collection<Long> memberIds,
            @Param("now") Instant now);
}
