package com.pitchmap.program.infra;

import com.pitchmap.program.domain.Payment;
import com.pitchmap.program.domain.PaymentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long>, PaymentRepository {

    // 도메인 리포지토리와 JpaRepository가 같은 이름의 메서드를 따로 선언해서, 구현체 타입으로 호출하면 모호해진다.
    @Override
    Payment saveAndFlush(Payment payment);

    @Override
    Optional<Payment> findByProgramApplicationId(long programApplicationId);

    @Override
    @Query("""
            select p.programApplicationId from Payment p
            where p.status = com.pitchmap.program.domain.PaymentStatus.PAID
              and p.programApplicationId in (
                  select a.id from ProgramApplication a where a.programId = :programId)
            """)
    List<Long> findPaidApplicationIdsByProgram(@Param("programId") long programId);

    // 행사 하나의 결제를 한 문장으로 모두 환불 처리한다. 신청 상태와 관계없이 PAID인 결제만 고른다.
    @Override
    @Modifying(flushAutomatically = true)
    @Query("""
            update Payment p
            set p.status = com.pitchmap.program.domain.PaymentStatus.REFUNDED,
                p.refundedAt = :now,
                p.updatedAt = :now
            where p.status = com.pitchmap.program.domain.PaymentStatus.PAID
              and p.programApplicationId in (
                  select a.id from ProgramApplication a where a.programId = :programId)
            """)
    int refundPaidByProgram(@Param("programId") long programId, @Param("now") Instant now);
}
