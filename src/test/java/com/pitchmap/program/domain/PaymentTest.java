package com.pitchmap.program.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.testsupport.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;

    @Test
    @DisplayName("[F-19] 결제를 만들면 PAID이고 결제 시각과 금액이 기록되며, 0원도 허용한다")
    void createsPaidPayment() {
        Payment payment = Payment.paid(7L, 0, NOW);

        assertThat(payment.getProgramApplicationId()).isEqualTo(7L);
        assertThat(payment.getAmount()).isZero();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPaidAt()).isEqualTo(NOW);
        assertThat(payment.getRefundedAt()).isNull();
    }

    @Test
    @DisplayName("[F-19][PG-06] 환불하면 REFUNDED가 되고 환불 시각이 기록되며, 다시 환불할 수 없다")
    void refundsOnce() {
        Payment payment = Payment.paid(7L, 30000, NOW);
        Instant refundAt = NOW.plus(Duration.ofHours(1));

        payment.refund(refundAt);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAt()).isEqualTo(refundAt);
        assertThatThrownBy(() -> payment.refund(refundAt)).isInstanceOf(IllegalStateException.class);
    }
}
