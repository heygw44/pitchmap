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
 * 신청 행은 선착순 신청 쿼리가 만들고, 이 클래스는 결제와 취소로 바뀌는 상태 전이를 맡는다.
 * 같은 신청을 동시에 바꾸는 요청은 직접 막지 않는다. 호출하는 서비스가 신청 행을 먼저 잠가야 한다.
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

    @Column(name = "review_requested_at")
    private Instant reviewRequestedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * 호출하면 결제 대기 신청을 CONFIRMED로 바꾸고 확정 시각을 now로 적는다.
     *
     * <p>이미 EXPIRED이거나, 결제 대기인데 now가 결제 기한 이후(기한 정각 포함)이면 PROGRAM_PAYMENT_EXPIRED를 던진다.
     * 이미 CONFIRMED이거나 CANCELED이면 PROGRAM_INVALID_STATE를 던진다. 던지면 신청은 바뀌지 않는다.
     */
    public void confirmPayment(Instant now) {
        boolean dueReached = status == ProgramApplicationStatus.PENDING_PAYMENT && !now.isBefore(paymentDueAt);
        if (status == ProgramApplicationStatus.EXPIRED || dueReached) {
            throw new ProgramException(ProgramErrorCode.PROGRAM_PAYMENT_EXPIRED);
        }
        if (status != ProgramApplicationStatus.PENDING_PAYMENT) {
            throw new ProgramException(ProgramErrorCode.PROGRAM_INVALID_STATE);
        }
        this.status = ProgramApplicationStatus.CONFIRMED;
        this.confirmedAt = now;
        this.updatedAt = now;
    }

    /**
     * 호출하면 신청자 본인의 취소로 신청을 CANCELED(사유 USER)로 바꾸고, 결제를 마친 신청이었으면 true를 돌려준다.
     *
     * <p>결제 대기 신청은 결제 기한이 지났어도 아직 만료 처리 전이면 취소할 수 있다. 확정 신청은 now가 refundDeadline 이전이거나
     * 같을 때만 취소할 수 있고, 그 뒤이면 PROGRAM_CANCEL_NOT_ALLOWED를 던진다. 이미 CANCELED이거나 EXPIRED이면
     * PROGRAM_INVALID_STATE를 던진다. 던지면 신청은 바뀌지 않는다.
     */
    public boolean cancelByMember(Instant now, Instant refundDeadline) {
        boolean wasConfirmed;
        switch (status) {
            case PENDING_PAYMENT -> wasConfirmed = false;
            case CONFIRMED -> {
                if (now.isAfter(refundDeadline)) {
                    throw new ProgramException(ProgramErrorCode.PROGRAM_CANCEL_NOT_ALLOWED);
                }
                wasConfirmed = true;
            }
            case CANCELED, EXPIRED -> throw new ProgramException(ProgramErrorCode.PROGRAM_INVALID_STATE);
            default -> throw new IllegalStateException("알 수 없는 신청 상태입니다: " + status);
        }
        this.status = ProgramApplicationStatus.CANCELED;
        this.cancelReason = ProgramCancelReason.USER;
        this.canceledAt = now;
        this.updatedAt = now;
        return wasConfirmed;
    }

    /**
     * 호출하면 신청자가 이용 정지를 받아 결제 대기 신청을 CANCELED(사유 SANCTIONED)로 바꾸고 취소 시각을 now로 적는다.
     * 결제 대기가 아닌 신청은 PROGRAM_INVALID_STATE를 던진다. 던지면 신청은 바뀌지 않는다.
     */
    public void cancelBySanction(Instant now) {
        if (status != ProgramApplicationStatus.PENDING_PAYMENT) {
            throw new ProgramException(ProgramErrorCode.PROGRAM_INVALID_STATE);
        }
        this.status = ProgramApplicationStatus.CANCELED;
        this.cancelReason = ProgramCancelReason.SANCTIONED;
        this.canceledAt = now;
        this.updatedAt = now;
    }
}
