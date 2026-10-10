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

/** 행사 신청 하나에 하나뿐인 가짜 결제다. 실제 결제 기관을 부르지 않는다. */
@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "program_application_id")
    private long programApplicationId;

    private int amount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    /** 호출하면 programApplicationId인 신청의 결제 완료 건을 만든다. amount는 결제한 시점의 행사 참가비이고 0원도 허용한다. */
    public static Payment paid(long programApplicationId, int amount, Instant now) {
        Payment payment = new Payment();
        payment.programApplicationId = programApplicationId;
        payment.amount = amount;
        payment.status = PaymentStatus.PAID;
        payment.paidAt = now;
        payment.createdAt = now;
        payment.updatedAt = now;
        return payment;
    }

    /** 호출하면 결제 완료 건을 REFUNDED로 바꾸고 환불 시각을 now로 적는다. 이미 환불한 결제이면 {@link IllegalStateException}을 던진다. */
    public void refund(Instant now) {
        if (status != PaymentStatus.PAID) {
            throw new IllegalStateException("결제 완료 상태의 결제만 환불할 수 있습니다.");
        }
        this.status = PaymentStatus.REFUNDED;
        this.refundedAt = now;
        this.updatedAt = now;
    }
}
