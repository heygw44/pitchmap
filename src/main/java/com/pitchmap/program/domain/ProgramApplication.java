package com.pitchmap.program.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원이 행사에 낸 신청이다. 결제 대기와 확정 상태인 신청이 정원을 차지한다.
 * 신청을 만들고 결제하는 규칙은 선착순 신청과 결제를 구현할 때 이 클래스에 더한다.
 */
@Entity
@Table(name = "program_application")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProgramApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "program_id")
    private long programId;

    @Column(name = "member_id")
    private long memberId;

    @Enumerated(EnumType.STRING)
    private ProgramApplicationStatus status;

    @Column(name = "payment_due_at")
    private Instant paymentDueAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Column(name = "cancel_reason")
    @Enumerated(EnumType.STRING)
    private ProgramCancelReason cancelReason;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
