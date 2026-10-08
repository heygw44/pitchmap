package com.pitchmap.program.infra;

import com.pitchmap.program.domain.ProgramApplication;
import com.pitchmap.program.domain.ProgramApplicationRepository;
import com.pitchmap.program.domain.ProgramCancelReason;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProgramApplicationJpaRepository
        extends JpaRepository<ProgramApplication, Long>, ProgramApplicationRepository {

    // 행사 하나의 신청을 한 문장으로 모두 바꾼다. 신청 엔티티를 하나씩 읽어 고치면 신청 수만큼 쿼리가 나간다.
    // flushAutomatically로 같은 트랜잭션에서 먼저 바꾼 행사 엔티티를 이 쿼리 전에 DB에 쓴다.
    @Override
    @Modifying(flushAutomatically = true)
    @Query("""
            update ProgramApplication a
            set a.status = com.pitchmap.program.domain.ProgramApplicationStatus.CANCELED,
                a.cancelReason = :reason,
                a.canceledAt = :now,
                a.updatedAt = :now
            where a.programId = :programId
              and a.status in (
                  com.pitchmap.program.domain.ProgramApplicationStatus.PENDING_PAYMENT,
                  com.pitchmap.program.domain.ProgramApplicationStatus.CONFIRMED)
            """)
    int cancelActiveByProgram(
            @Param("programId") long programId, @Param("reason") ProgramCancelReason reason, @Param("now") Instant now);
}
