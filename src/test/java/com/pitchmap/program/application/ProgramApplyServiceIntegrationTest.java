package com.pitchmap.program.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.error.ErrorCode;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.program.domain.ProgramErrorCode;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ProgramApplyServiceIntegrationTest {

    @Autowired
    private ProgramApplyService programApplyService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private ProgramApplyFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ProgramApplyFixture(jdbc, memberRepository);
    }

    @Test
    @DisplayName("[F-18][PG-03][PG-04] 신청하면 결제 대기 신청이 생기고 결제 기한은 신청 시각에 결제 기한(분)을 더한 시각이다")
    void applyCreatesPendingPaymentApplication() {
        // given
        long programId = fixture.saveProgram(3, false);
        long memberId = fixture.saveMember();

        // when
        ProgramApplyResult result = programApplyService.apply(memberId, programId);

        // then
        assertThat(result.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(result.amount()).isEqualTo(30000);
        assertThat(result.paymentDueAt()).isEqualTo(MutableClock.DEFAULT_INSTANT.plus(Duration.ofMinutes(15)));
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM program_application WHERE id = ?", String.class, result.applicationId()))
                .isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject(
                        "SELECT payment_due_at FROM program_application WHERE id = ?",
                        LocalDateTime.class,
                        result.applicationId()))
                .isEqualTo(LocalDateTime.of(2026, 10, 5, 3, 15));
        assertThat(fixture.activeCount(programId)).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-18][PG-03] 신청 시작 전과 신청 마감 뒤에는 PROGRAM_NOT_IN_APPLY_PERIOD이다")
    void rejectsOutsideApplyPeriod() {
        // given
        long beforeOpen = fixture.saveProgram(
                3,
                false,
                MutableClock.DEFAULT_INSTANT.plus(Duration.ofHours(1)),
                MutableClock.DEFAULT_INSTANT.plus(Duration.ofDays(1)),
                "SCHEDULED");
        long afterClose = fixture.saveProgram(
                3,
                false,
                MutableClock.DEFAULT_INSTANT.minus(Duration.ofDays(1)),
                MutableClock.DEFAULT_INSTANT,
                "SCHEDULED");
        long memberId = fixture.saveMember();

        // then
        assertErrorCode(
                () -> programApplyService.apply(memberId, beforeOpen), ProgramErrorCode.PROGRAM_NOT_IN_APPLY_PERIOD);
        assertErrorCode(
                () -> programApplyService.apply(memberId, afterClose), ProgramErrorCode.PROGRAM_NOT_IN_APPLY_PERIOD);
        assertThat(fixture.applicationCount(beforeOpen, memberId)).isZero();
        assertThat(fixture.applicationCount(afterClose, memberId)).isZero();
    }

    @Test
    @DisplayName("[F-18] 취소된 행사에 신청하면 PROGRAM_INVALID_STATE이다")
    void rejectsCanceledProgram() {
        long programId = fixture.saveProgram(
                3,
                false,
                MutableClock.DEFAULT_INSTANT.minus(Duration.ofHours(1)),
                MutableClock.DEFAULT_INSTANT.plus(Duration.ofDays(5)),
                "CANCELED");
        long memberId = fixture.saveMember();

        assertErrorCode(() -> programApplyService.apply(memberId, programId), ProgramErrorCode.PROGRAM_INVALID_STATE);
    }

    @Test
    @DisplayName("[F-18] 없는 행사에 신청하면 NOT_FOUND이다")
    void rejectsMissingProgram() {
        long memberId = fixture.saveMember();

        assertErrorCode(() -> programApplyService.apply(memberId, 999_999L), CommonErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("[F-18][PG-02] 숙박 행사는 신뢰 단계 0이면 TRUST_LEVEL_INSUFFICIENT, 단계 1이면 성공한다")
    void overnightProgramRequiresTrustLevelOne() {
        // given
        long programId = fixture.saveProgram(3, true);
        long level0 = fixture.saveMember();
        long level1 = fixture.saveVerifiedMember();

        // then
        assertErrorCode(() -> programApplyService.apply(level0, programId), CommonErrorCode.TRUST_LEVEL_INSUFFICIENT);
        assertThat(fixture.applicationCount(programId, level0)).isZero();
        assertThat(programApplyService.apply(level1, programId).status()).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    @DisplayName("[F-18][PG-02] 숙박이 없는 행사는 신뢰 단계 0 회원도 신청할 수 있다")
    void dayProgramAllowsTrustLevelZero() {
        long programId = fixture.saveProgram(3, false);
        long memberId = fixture.saveMember();

        assertThat(programApplyService.apply(memberId, programId).status()).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    @DisplayName("[F-18][PG-03] 이미 결제 대기·확정 신청이 있으면 PROGRAM_ALREADY_APPLIED이고, 취소·만료된 뒤에는 다시 신청할 수 있다")
    void allowsOneActiveApplicationPerMember() {
        // given
        long programId = fixture.saveProgram(5, false);
        long memberId = fixture.saveMember();
        ProgramApplyResult first = programApplyService.apply(memberId, programId);

        // when, then
        assertErrorCode(() -> programApplyService.apply(memberId, programId), ProgramErrorCode.PROGRAM_ALREADY_APPLIED);

        jdbc.update("UPDATE program_application SET status = 'CANCELED' WHERE id = ?", first.applicationId());
        ProgramApplyResult second = programApplyService.apply(memberId, programId);
        assertThat(second.applicationId()).isNotEqualTo(first.applicationId());

        jdbc.update("UPDATE program_application SET status = 'EXPIRED' WHERE id = ?", second.applicationId());
        assertThat(programApplyService.apply(memberId, programId).status()).isEqualTo("PENDING_PAYMENT");
        assertThat(fixture.applicationCount(programId, memberId)).isEqualTo(3);
        assertThat(fixture.activeCount(programId)).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-18][PG-03] 자리가 없으면 PROGRAM_SOLD_OUT이고, 이미 신청한 회원에게는 자리가 없어도 PROGRAM_ALREADY_APPLIED이다")
    void soldOutAndAlreadyAppliedOrder() {
        // given
        long programId = fixture.saveProgram(1, false);
        long first = fixture.saveMember();
        long second = fixture.saveMember();
        programApplyService.apply(first, programId);

        // then
        assertErrorCode(() -> programApplyService.apply(second, programId), ProgramErrorCode.PROGRAM_SOLD_OUT);
        assertErrorCode(() -> programApplyService.apply(first, programId), ProgramErrorCode.PROGRAM_ALREADY_APPLIED);
        assertThat(fixture.activeCount(programId)).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-18][PG-05] 취소된 신청은 정원을 차지하지 않아 자리가 다시 난다")
    void canceledApplicationFreesSeat() {
        long programId = fixture.saveProgram(1, false);
        long first = fixture.saveMember();
        long second = fixture.saveMember();
        ProgramApplyResult firstResult = programApplyService.apply(first, programId);

        jdbc.update("UPDATE program_application SET status = 'CANCELED' WHERE id = ?", firstResult.applicationId());

        assertThat(programApplyService.apply(second, programId).status()).isEqualTo("PENDING_PAYMENT");
    }

    private static void assertErrorCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }
}
