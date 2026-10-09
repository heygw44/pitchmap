package com.pitchmap.program.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.testsupport.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class ProgramApplicationTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;
    private static final Instant DUE = NOW.plus(Duration.ofMinutes(15));
    private static final Instant REFUND_DEADLINE = NOW.plus(Duration.ofDays(4));

    @Test
    @DisplayName("[F-19][PG-04] 결제 기한 직전에 결제하면 확정되고 확정 시각이 기록된다")
    void confirmsJustBeforeDue() {
        ProgramApplication application = application(ProgramApplicationStatus.PENDING_PAYMENT);
        Instant payAt = DUE.minusNanos(1_000);

        application.confirmPayment(payAt);

        assertThat(application.getStatus()).isEqualTo(ProgramApplicationStatus.CONFIRMED);
        assertThat(application.getConfirmedAt()).isEqualTo(payAt);
        assertThat(application.getUpdatedAt()).isEqualTo(payAt);
    }

    @Test
    @DisplayName("[F-19][PG-04] 결제 기한 정각에 결제하면 PROGRAM_PAYMENT_EXPIRED이고 신청은 바뀌지 않는다")
    void rejectsAtDueInstant() {
        ProgramApplication application = application(ProgramApplicationStatus.PENDING_PAYMENT);

        assertThatThrownBy(() -> application.confirmPayment(DUE))
                .isInstanceOfSatisfying(
                        ProgramException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ProgramErrorCode.PROGRAM_PAYMENT_EXPIRED));
        assertThat(application.getStatus()).isEqualTo(ProgramApplicationStatus.PENDING_PAYMENT);
        assertThat(application.getConfirmedAt()).isNull();
    }

    @Test
    @DisplayName("[F-19] 만료된 신청은 PROGRAM_PAYMENT_EXPIRED, 확정·취소된 신청은 PROGRAM_INVALID_STATE로 결제를 거부한다")
    void rejectsPaymentByState() {
        assertConfirmFails(ProgramApplicationStatus.EXPIRED, ProgramErrorCode.PROGRAM_PAYMENT_EXPIRED);
        assertConfirmFails(ProgramApplicationStatus.CONFIRMED, ProgramErrorCode.PROGRAM_INVALID_STATE);
        assertConfirmFails(ProgramApplicationStatus.CANCELED, ProgramErrorCode.PROGRAM_INVALID_STATE);
    }

    @Test
    @DisplayName("[F-19][PG-06] 결제 대기 신청은 결제 기한이 지났어도 취소할 수 있고 환불 대상이 아니다")
    void cancelsPendingEvenAfterDue() {
        ProgramApplication application = application(ProgramApplicationStatus.PENDING_PAYMENT);
        Instant cancelAt = DUE.plus(Duration.ofMinutes(5));

        boolean refund = application.cancelByMember(cancelAt, REFUND_DEADLINE);

        assertThat(refund).isFalse();
        assertThat(application.getStatus()).isEqualTo(ProgramApplicationStatus.CANCELED);
        assertThat(application.getCancelReason()).isEqualTo(ProgramCancelReason.USER);
        assertThat(application.getCanceledAt()).isEqualTo(cancelAt);
    }

    @Test
    @DisplayName("[F-19][PG-06] 확정 신청은 환불 기한 정각까지 취소할 수 있고 환불 대상이다")
    void cancelsConfirmedAtRefundDeadline() {
        ProgramApplication application = application(ProgramApplicationStatus.CONFIRMED);

        boolean refund = application.cancelByMember(REFUND_DEADLINE, REFUND_DEADLINE);

        assertThat(refund).isTrue();
        assertThat(application.getStatus()).isEqualTo(ProgramApplicationStatus.CANCELED);
        assertThat(application.getCancelReason()).isEqualTo(ProgramCancelReason.USER);
    }

    @Test
    @DisplayName("[F-19][PG-06] 확정 신청을 환불 기한 1초 뒤에 취소하면 PROGRAM_CANCEL_NOT_ALLOWED이고 신청은 바뀌지 않는다")
    void rejectsConfirmedCancelAfterRefundDeadline() {
        ProgramApplication application = application(ProgramApplicationStatus.CONFIRMED);

        assertThatThrownBy(() -> application.cancelByMember(REFUND_DEADLINE.plusSeconds(1), REFUND_DEADLINE))
                .isInstanceOfSatisfying(
                        ProgramException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ProgramErrorCode.PROGRAM_CANCEL_NOT_ALLOWED));
        assertThat(application.getStatus()).isEqualTo(ProgramApplicationStatus.CONFIRMED);
        assertThat(application.getCancelReason()).isNull();
    }

    @Test
    @DisplayName("[F-19] 이미 취소되거나 만료된 신청을 취소하면 PROGRAM_INVALID_STATE이다")
    void rejectsCancelOfFinishedApplication() {
        for (ProgramApplicationStatus status :
                new ProgramApplicationStatus[] {ProgramApplicationStatus.CANCELED, ProgramApplicationStatus.EXPIRED}) {
            ProgramApplication application = application(status);

            assertThatThrownBy(() -> application.cancelByMember(NOW, REFUND_DEADLINE))
                    .isInstanceOfSatisfying(
                            ProgramException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ProgramErrorCode.PROGRAM_INVALID_STATE));
            assertThat(application.getStatus()).isEqualTo(status);
        }
    }

    private static void assertConfirmFails(ProgramApplicationStatus status, ProgramErrorCode expected) {
        ProgramApplication application = application(status);

        assertThatThrownBy(() -> application.confirmPayment(NOW))
                .isInstanceOfSatisfying(
                        ProgramException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
        assertThat(application.getStatus()).isEqualTo(status);
    }

    // 엔티티에 만드는 메서드가 없어서(신청 행은 쿼리가 만든다) 리플렉션으로 상태를 채운다.
    private static ProgramApplication application(ProgramApplicationStatus status) {
        ProgramApplication application = BeanUtils.instantiateClass(ProgramApplication.class);
        ReflectionTestUtils.setField(application, "status", status);
        ReflectionTestUtils.setField(application, "paymentDueAt", DUE);
        return application;
    }
}
