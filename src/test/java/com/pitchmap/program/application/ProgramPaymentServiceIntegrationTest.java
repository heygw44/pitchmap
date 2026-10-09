package com.pitchmap.program.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.error.ErrorCode;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.program.domain.ProgramErrorCode;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ProgramPaymentServiceIntegrationTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;

    @Autowired
    private ProgramPaymentService programPaymentService;

    @Autowired
    private ProgramApplyService programApplyService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    private ProgramApplyFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ProgramApplyFixture(jdbc, memberRepository);
    }

    @Test
    @DisplayName("[F-19][PG-04] 결제하면 신청이 확정되고 결제 행과 확정 이벤트가 생기며, 금액은 결제 시점의 참가비이다")
    void payConfirmsApplication() {
        // given
        long programId = fixture.saveProgram(3, false);
        long memberId = fixture.saveMember();
        long applicationId = programApplyService.apply(memberId, programId).applicationId();
        jdbc.update("UPDATE program SET fee = 45000 WHERE id = ?", programId);
        clock.advance(Duration.ofMinutes(5));

        // when
        ProgramPayResult result = programPaymentService.pay(memberId, applicationId);

        // then
        assertThat(result.applicationId()).isEqualTo(applicationId);
        assertThat(result.status()).isEqualTo("CONFIRMED");
        assertThat(result.paidAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(fixture.applicationStatus(applicationId)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject(
                        "SELECT confirmed_at FROM program_application WHERE id = ?",
                        java.time.LocalDateTime.class,
                        applicationId))
                .isEqualTo(java.time.LocalDateTime.of(2026, 10, 5, 3, 5));
        assertThat(fixture.paymentCount(applicationId)).isEqualTo(1);
        assertThat(fixture.paymentStatus(applicationId)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject(
                        "SELECT amount FROM payment WHERE program_application_id = ?", Integer.class, applicationId))
                .isEqualTo(45000);
        String payload = jdbc.queryForObject(
                "SELECT payload FROM outbox_event WHERE event_type = 'PROGRAM_APPLICATION_CONFIRMED'"
                        + " AND aggregate_type = 'PROGRAM_APPLICATION' AND aggregate_id = ?",
                String.class,
                applicationId);
        assertThat(JsonPath.<Number>read(payload, "$.applicationId").longValue())
                .isEqualTo(applicationId);
        assertThat(JsonPath.<Number>read(payload, "$.memberId").longValue()).isEqualTo(memberId);
        assertThat(JsonPath.<Number>read(payload, "$.programId").longValue()).isEqualTo(programId);
    }

    @Test
    @DisplayName("[F-19] 참가비가 0원인 행사도 결제하면 금액 0원의 결제 행이 생긴다")
    void freeProgramCreatesZeroPayment() {
        long programId = fixture.saveProgram(3, false);
        jdbc.update("UPDATE program SET fee = 0 WHERE id = ?", programId);
        long memberId = fixture.saveMember();
        long applicationId = programApplyService.apply(memberId, programId).applicationId();

        programPaymentService.pay(memberId, applicationId);

        assertThat(jdbc.queryForObject(
                        "SELECT amount FROM payment WHERE program_application_id = ?", Integer.class, applicationId))
                .isZero();
        assertThat(fixture.applicationStatus(applicationId)).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("[F-19][PG-04] 결제 기한 직전에는 결제할 수 있고, 기한 정각에는 PROGRAM_PAYMENT_EXPIRED이다")
    void dueBoundary() {
        // given
        long programId = fixture.saveProgram(3, false);
        long beforeMember = fixture.saveMember();
        long atMember = fixture.saveMember();
        Instant due = NOW.plus(Duration.ofMinutes(15));
        long before = fixture.saveApplication(programId, beforeMember, "PENDING_PAYMENT", due);
        long at = fixture.saveApplication(programId, atMember, "PENDING_PAYMENT", due);

        // when, then
        clock.setInstant(due.minusSeconds(1));
        assertThat(programPaymentService.pay(beforeMember, before).status()).isEqualTo("CONFIRMED");
        clock.setInstant(due);
        assertErrorCode(() -> programPaymentService.pay(atMember, at), ProgramErrorCode.PROGRAM_PAYMENT_EXPIRED);
        assertThat(fixture.applicationStatus(at)).isEqualTo("PENDING_PAYMENT");
        assertThat(fixture.paymentCount(at)).isZero();
    }

    @Test
    @DisplayName("[F-19] 만료된 신청은 PROGRAM_PAYMENT_EXPIRED, 확정·취소된 신청은 PROGRAM_INVALID_STATE로 결제를 거부하고 결제 행을 만들지 않는다")
    void payRejectedByState() {
        long programId = fixture.saveProgram(5, false);
        long expiredMember = fixture.saveMember();
        long confirmedMember = fixture.saveMember();
        long canceledMember = fixture.saveMember();
        long expired = fixture.saveApplication(programId, expiredMember, "EXPIRED");
        long confirmed = fixture.saveApplication(programId, confirmedMember, "CONFIRMED");
        long canceled = fixture.saveApplication(programId, canceledMember, "CANCELED");

        assertErrorCode(
                () -> programPaymentService.pay(expiredMember, expired), ProgramErrorCode.PROGRAM_PAYMENT_EXPIRED);
        assertErrorCode(
                () -> programPaymentService.pay(confirmedMember, confirmed), ProgramErrorCode.PROGRAM_INVALID_STATE);
        assertErrorCode(
                () -> programPaymentService.pay(canceledMember, canceled), ProgramErrorCode.PROGRAM_INVALID_STATE);
        assertThat(fixture.paymentCount(expired) + fixture.paymentCount(confirmed) + fixture.paymentCount(canceled))
                .isZero();
        assertThat(fixture.outboxCount("PROGRAM_APPLICATION_CONFIRMED", expired))
                .isZero();
    }

    @Test
    @DisplayName("[F-19] 없는 신청은 NOT_FOUND, 남의 신청은 ACCESS_DENIED이고 신청은 바뀌지 않는다")
    void payChecksExistenceThenOwner() {
        long programId = fixture.saveProgram(3, false);
        long owner = fixture.saveMember();
        long other = fixture.saveMember();
        long applicationId = programApplyService.apply(owner, programId).applicationId();

        assertErrorCode(() -> programPaymentService.pay(owner, 999_999L), CommonErrorCode.NOT_FOUND);
        assertErrorCode(() -> programPaymentService.pay(other, applicationId), CommonErrorCode.ACCESS_DENIED);
        assertErrorCode(() -> programPaymentService.cancel(other, applicationId), CommonErrorCode.ACCESS_DENIED);
        assertThat(fixture.applicationStatus(applicationId)).isEqualTo("PENDING_PAYMENT");
        assertThat(fixture.paymentCount(applicationId)).isZero();
    }

    @Test
    @DisplayName("[F-19][PG-06] 결제 대기 신청을 취소하면 환불 없이 CANCELED(사유 USER)가 되고, 결제 기한이 지난 뒤에도 취소할 수 있다")
    void cancelPendingApplication() {
        // given
        long programId = fixture.saveProgram(1, false);
        long memberId = fixture.saveMember();
        long applicationId = programApplyService.apply(memberId, programId).applicationId();
        clock.advance(Duration.ofMinutes(30));

        // when
        ProgramApplicationCancelResult result = programPaymentService.cancel(memberId, applicationId);

        // then
        assertThat(result.status()).isEqualTo("CANCELED");
        assertThat(result.refunded()).isFalse();
        assertThat(fixture.applicationStatus(applicationId)).isEqualTo("CANCELED");
        assertThat(jdbc.queryForObject(
                        "SELECT cancel_reason FROM program_application WHERE id = ?", String.class, applicationId))
                .isEqualTo("USER");
        assertThat(fixture.paymentCount(applicationId)).isZero();
        String payload = jdbc.queryForObject(
                "SELECT payload FROM outbox_event WHERE event_type = 'PROGRAM_APPLICATION_CANCELED'"
                        + " AND aggregate_id = ?",
                String.class,
                applicationId);
        assertThat(JsonPath.<String>read(payload, "$.reason")).isEqualTo("USER");
        assertThat(JsonPath.<Boolean>read(payload, "$.refunded")).isFalse();
    }

    @Test
    @DisplayName("[F-19][PG-06] 확정 신청을 환불 기한 정각에 취소하면 결제가 REFUNDED가 되고, 자리가 돌아와 다른 회원이 신청할 수 있다")
    void cancelConfirmedApplicationRefundsAndFreesSeat() {
        // given
        long programId = fixture.saveProgram(1, false);
        long memberId = fixture.saveMember();
        long other = fixture.saveMember();
        long applicationId = programApplyService.apply(memberId, programId).applicationId();
        programPaymentService.pay(memberId, applicationId);
        Instant refundDeadline = NOW.plus(Duration.ofDays(7)).minus(Duration.ofHours(72));
        clock.setInstant(refundDeadline);

        // when
        ProgramApplicationCancelResult result = programPaymentService.cancel(memberId, applicationId);

        // then
        assertThat(result.status()).isEqualTo("CANCELED");
        assertThat(result.refunded()).isTrue();
        assertThat(fixture.paymentStatus(applicationId)).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject(
                        "SELECT refunded_at IS NOT NULL FROM payment WHERE program_application_id = ?",
                        Boolean.class,
                        applicationId))
                .isTrue();
        String payload = jdbc.queryForObject(
                "SELECT payload FROM outbox_event WHERE event_type = 'PROGRAM_APPLICATION_CANCELED'"
                        + " AND aggregate_id = ?",
                String.class,
                applicationId);
        assertThat(JsonPath.<Boolean>read(payload, "$.refunded")).isTrue();
        assertThat(fixture.activeCount(programId)).isZero();
        assertThat(programApplyService.apply(other, programId).status()).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    @DisplayName("[F-19][PG-06] 확정 신청을 환불 기한 1초 뒤에 취소하면 PROGRAM_CANCEL_NOT_ALLOWED이고 신청과 결제는 그대로이다")
    void cancelConfirmedAfterRefundDeadlineIsRejected() {
        long programId = fixture.saveProgram(1, false);
        long memberId = fixture.saveMember();
        long applicationId = programApplyService.apply(memberId, programId).applicationId();
        programPaymentService.pay(memberId, applicationId);
        clock.setInstant(
                NOW.plus(Duration.ofDays(7)).minus(Duration.ofHours(72)).plusSeconds(1));

        assertErrorCode(
                () -> programPaymentService.cancel(memberId, applicationId),
                ProgramErrorCode.PROGRAM_CANCEL_NOT_ALLOWED);

        assertThat(fixture.applicationStatus(applicationId)).isEqualTo("CONFIRMED");
        assertThat(fixture.paymentStatus(applicationId)).isEqualTo("PAID");
        assertThat(fixture.outboxCount("PROGRAM_APPLICATION_CANCELED", applicationId))
                .isZero();
    }

    @Test
    @DisplayName("[F-19] 이미 취소한 신청을 다시 취소하거나 만료된 신청을 취소하면 PROGRAM_INVALID_STATE이다")
    void cancelRejectsFinishedApplication() {
        long programId = fixture.saveProgram(3, false);
        long memberId = fixture.saveMember();
        long expiredMember = fixture.saveMember();
        long applicationId = programApplyService.apply(memberId, programId).applicationId();
        long expired = fixture.saveApplication(programId, expiredMember, "EXPIRED");
        programPaymentService.cancel(memberId, applicationId);

        assertErrorCode(
                () -> programPaymentService.cancel(memberId, applicationId), ProgramErrorCode.PROGRAM_INVALID_STATE);
        assertErrorCode(
                () -> programPaymentService.cancel(expiredMember, expired), ProgramErrorCode.PROGRAM_INVALID_STATE);
        assertThat(fixture.outboxCount("PROGRAM_APPLICATION_CANCELED", applicationId))
                .isEqualTo(1);
    }

    private static void assertErrorCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }
}
