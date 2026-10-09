package com.pitchmap.program.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.program.domain.ProgramErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ProgramPaymentExpiryServiceIntegrationTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;
    private static final String EXPIRED_EVENT = "PROGRAM_APPLICATION_EXPIRED";

    @Autowired
    private ProgramPaymentExpiryService expiryService;

    @Autowired
    private ProgramApplyService programApplyService;

    @Autowired
    private ProgramPaymentService programPaymentService;

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
    @DisplayName("[F-19][PG-05] 결제 기한이 지난 결제 대기 신청은 만료되고, 사유와 시각이 남고, 자리가 돌아가며, 만료 이벤트가 1건 생긴다")
    void expiresDuePendingApplication() {
        // given
        long programId = fixture.saveProgram(1, false);
        long memberId = fixture.saveMember();
        long applicationId =
                fixture.saveApplication(programId, memberId, "PENDING_PAYMENT", NOW.minus(Duration.ofMinutes(1)));
        assertThat(fixture.activeCount(programId)).isEqualTo(1);

        // when
        ProgramPaymentExpiryService.Result result = expiryService.run();

        // then
        assertThat(result.expired()).isEqualTo(1);
        assertThat(fixture.applicationStatus(applicationId)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject(
                        "SELECT cancel_reason FROM program_application WHERE id = ?", String.class, applicationId))
                .isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject(
                        "SELECT canceled_at FROM program_application WHERE id = ?", LocalDateTime.class, applicationId))
                .isEqualTo(LocalDateTime.of(2026, 10, 5, 3, 0));
        assertThat(fixture.activeCount(programId)).isZero();
        assertThat(fixture.outboxCount(EXPIRED_EVENT, applicationId)).isEqualTo(1);
        String payload = jdbc.queryForObject(
                "SELECT payload FROM outbox_event WHERE event_type = ? AND aggregate_type = 'PROGRAM_APPLICATION'"
                        + " AND aggregate_id = ?",
                String.class,
                EXPIRED_EVENT,
                applicationId);
        assertThat(JsonPath.<Number>read(payload, "$.applicationId").longValue())
                .isEqualTo(applicationId);
        assertThat(JsonPath.<Number>read(payload, "$.memberId").longValue()).isEqualTo(memberId);
        assertThat(JsonPath.<Number>read(payload, "$.programId").longValue()).isEqualTo(programId);
    }

    @Test
    @DisplayName("[F-19][PG-05] 결제 기한 정각인 신청도 만료되고, 한 번 더 실행해도 이벤트는 늘지 않는다")
    void expiresExactlyAtDueAndIsIdempotent() {
        // given
        long programId = fixture.saveProgram(2, false);
        long memberId = fixture.saveMember();
        long applicationId = fixture.saveApplication(programId, memberId, "PENDING_PAYMENT", NOW);

        // when
        int first = expiryService.run().expired();
        int second = expiryService.run().expired();

        // then
        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(fixture.applicationStatus(applicationId)).isEqualTo("EXPIRED");
        assertThat(fixture.outboxCount(EXPIRED_EVENT, applicationId)).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-19][PG-05] 기한 전인 결제 대기, 확정, 취소 신청은 건드리지 않는다")
    void leavesOtherApplicationsUntouched() {
        // given
        long programId = fixture.saveProgram(5, false);
        Instant past = NOW.minus(Duration.ofMinutes(30));
        long beforeDue = fixture.saveApplication(
                programId, fixture.saveMember(), "PENDING_PAYMENT", NOW.plus(Duration.ofSeconds(1)));
        long confirmed = fixture.saveApplication(programId, fixture.saveMember(), "CONFIRMED", past);
        long canceled = fixture.saveApplication(programId, fixture.saveMember(), "CANCELED", past);

        // when
        int expired = expiryService.run().expired();

        // then
        assertThat(expired).isZero();
        assertThat(fixture.applicationStatus(beforeDue)).isEqualTo("PENDING_PAYMENT");
        assertThat(fixture.applicationStatus(confirmed)).isEqualTo("CONFIRMED");
        assertThat(fixture.applicationStatus(canceled)).isEqualTo("CANCELED");
        assertThat(fixture.outboxCount(EXPIRED_EVENT, beforeDue)).isZero();
        assertThat(fixture.outboxCount(EXPIRED_EVENT, confirmed)).isZero();
        assertThat(fixture.outboxCount(EXPIRED_EVENT, canceled)).isZero();
    }

    @Test
    @DisplayName("[F-19][PG-05] 신청 후 결제 기한이 지나 만료되면 같은 회원이 다시 신청할 수 있다")
    void memberCanApplyAgainAfterExpiry() {
        // given
        long programId = fixture.saveProgram(1, false);
        long memberId = fixture.saveMember();
        long firstId = programApplyService.apply(memberId, programId).applicationId();
        clock.advance(Duration.ofMinutes(15));

        // when
        expiryService.run();
        long secondId = programApplyService.apply(memberId, programId).applicationId();

        // then
        assertThat(secondId).isNotEqualTo(firstId);
        assertThat(fixture.applicationStatus(firstId)).isEqualTo("EXPIRED");
        assertThat(fixture.applicationStatus(secondId)).isEqualTo("PENDING_PAYMENT");
        assertThat(fixture.activeCount(programId)).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-19][PG-05] 만료된 신청을 결제하면 PROGRAM_PAYMENT_EXPIRED이고 결제 행은 생기지 않는다")
    void payingExpiredApplicationFails() {
        // given
        long programId = fixture.saveProgram(1, false);
        long memberId = fixture.saveMember();
        long applicationId = programApplyService.apply(memberId, programId).applicationId();
        clock.advance(Duration.ofMinutes(16));
        expiryService.run();

        // when, then
        assertThatThrownBy(() -> programPaymentService.pay(memberId, applicationId))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ProgramErrorCode.PROGRAM_PAYMENT_EXPIRED));
        assertThat(fixture.paymentCount(applicationId)).isZero();
        assertThat(fixture.applicationStatus(applicationId)).isEqualTo("EXPIRED");
    }
}
