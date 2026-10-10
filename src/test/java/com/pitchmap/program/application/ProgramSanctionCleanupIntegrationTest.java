package com.pitchmap.program.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.notification.application.OutboxPublisher;
import com.pitchmap.program.domain.ProgramCancelReason;
import com.pitchmap.trust.application.SanctionConfirmCommand;
import com.pitchmap.trust.application.SanctionConfirmService;
import com.pitchmap.trust.domain.SanctionType;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ProgramSanctionCleanupIntegrationTest {

    @Autowired
    private ProgramSanctionCleanupService cleanupService;

    @Autowired
    private SanctionConfirmService sanctionConfirmService;

    @Autowired
    private OutboxPublisher outboxPublisher;

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
    @DisplayName(
            "[F-16][SN-14] 정지된 회원의 결제 대기 신청만 SANCTIONED로 취소하고, 확정 신청은 확인 요청 시각만 적으며, 이미 취소한 신청과 다른 회원의 신청은 건드리지 않는다")
    void cancelsPendingAndMarksConfirmedForReview() {
        // given
        long member = fixture.saveMember();
        long other = fixture.saveMember();
        long programA = fixture.saveProgram(3, false);
        long programB = fixture.saveProgram(3, false);
        long programC = fixture.saveProgram(3, false);
        long programD = fixture.saveProgram(3, false);
        long pendingA = fixture.saveApplication(programA, member, "PENDING_PAYMENT");
        long pendingB = fixture.saveApplication(programB, member, "PENDING_PAYMENT");
        long confirmed = fixture.saveApplication(programC, member, "CONFIRMED");
        long alreadyCanceled = fixture.saveApplication(programD, member, "CANCELED");
        long otherPending = fixture.saveApplication(programA, other, "PENDING_PAYMENT");
        long otherConfirmed = fixture.saveApplication(programC, other, "CONFIRMED");

        // when
        cleanupService.cleanUp(member, ProgramCancelReason.SANCTIONED);

        // then
        assertThat(fixture.applicationStatus(pendingA)).isEqualTo("CANCELED");
        assertThat(fixture.applicationStatus(pendingB)).isEqualTo("CANCELED");
        assertThat(cancelReason(pendingA)).isEqualTo("SANCTIONED");
        assertThat(cancelReason(pendingB)).isEqualTo("SANCTIONED");
        assertThat(fixture.outboxCount("PROGRAM_APPLICATION_CANCELED", pendingA))
                .isEqualTo(1);
        assertThat(fixture.outboxCount("PROGRAM_APPLICATION_CANCELED", pendingB))
                .isEqualTo(1);
        assertCanceledPayload(pendingA, member, programA);
        assertThat(fixture.applicationStatus(confirmed)).isEqualTo("CONFIRMED");
        assertThat(reviewRequestedAt(confirmed)).isEqualTo(LocalDateTime.of(2026, 10, 5, 3, 0));
        assertThat(fixture.outboxCount("PROGRAM_APPLICATION_CANCELED", confirmed))
                .isZero();
        assertThat(fixture.applicationStatus(alreadyCanceled)).isEqualTo("CANCELED");
        assertThat(cancelReason(alreadyCanceled)).isNull();
        assertThat(fixture.outboxCount("PROGRAM_APPLICATION_CANCELED", alreadyCanceled))
                .isZero();
        assertThat(fixture.applicationStatus(otherPending)).isEqualTo("PENDING_PAYMENT");
        assertThat(fixture.applicationStatus(otherConfirmed)).isEqualTo("CONFIRMED");
        assertThat(reviewRequestedAt(otherConfirmed)).isNull();
        assertThat(reviewRequestedAt(pendingA)).isNull();
    }

    @Test
    @DisplayName("[F-16][SN-14][F-20] 취소로 돌아온 자리는 빈자리 알림 신청자에게 알리는 이벤트로 기록된다")
    void recordsSeatReleasedForVacancyAlertSubscriber() {
        long member = fixture.saveMember();
        long waiting = fixture.saveMember();
        long programId = fixture.saveProgram(1, false);
        fixture.saveApplication(programId, member, "PENDING_PAYMENT");
        fixture.saveVacancyAlert(programId, waiting);

        cleanupService.cleanUp(member, ProgramCancelReason.SANCTIONED);

        List<String> payloads = fixture.seatReleasedPayloads(programId);
        assertThat(payloads).hasSize(1);
        List<Number> memberIds = JsonPath.read(payloads.get(0), "$.memberIds");
        assertThat(memberIds.stream().map(Number::longValue).toList()).containsExactly(waiting);
    }

    @Test
    @DisplayName("[F-16][SN-14][NFR-04] 정리를 두 번 실행해도 이벤트가 늘지 않고 처음 기록한 확인 요청 시각이 유지된다")
    void rerunChangesNothing() {
        long member = fixture.saveMember();
        long programA = fixture.saveProgram(3, false);
        long programB = fixture.saveProgram(3, false);
        long pending = fixture.saveApplication(programA, member, "PENDING_PAYMENT");
        long confirmed = fixture.saveApplication(programB, member, "CONFIRMED");
        cleanupService.cleanUp(member, ProgramCancelReason.SANCTIONED);
        LocalDateTime firstReviewAt = reviewRequestedAt(confirmed);
        LocalDateTime firstCanceledAt = jdbc.queryForObject(
                "SELECT canceled_at FROM program_application WHERE id = ?", LocalDateTime.class, pending);

        clock.advance(Duration.ofHours(1));
        cleanupService.cleanUp(member, ProgramCancelReason.SANCTIONED);

        assertThat(fixture.outboxCount("PROGRAM_APPLICATION_CANCELED", pending)).isEqualTo(1);
        assertThat(reviewRequestedAt(confirmed)).isEqualTo(firstReviewAt);
        assertThat(jdbc.queryForObject(
                        "SELECT canceled_at FROM program_application WHERE id = ?", LocalDateTime.class, pending))
                .isEqualTo(firstCanceledAt);
    }

    @Test
    @DisplayName("[F-16][SN-14] 정지를 확정하고 발행기가 돌면 결제 대기 신청이 취소되고 신청자의 알림함에 취소 알림이 생긴다")
    void suspensionCancelsPendingApplicationThroughOutbox() {
        // given
        long admin = fixture.saveVerifiedMember();
        long target = fixture.saveVerifiedMember();
        long programId = fixture.saveProgram(3, false);
        long pending = fixture.saveApplication(programId, target, "PENDING_PAYMENT");

        // when
        sanctionConfirmService.confirm(
                new SanctionConfirmCommand(target, null, SanctionType.PERMANENT, "금전 요구", admin));
        assertThat(fixture.applicationStatus(pending)).isEqualTo("PENDING_PAYMENT");
        outboxPublisher.publishPending();
        outboxPublisher.publishPending();

        // then
        assertThat(fixture.applicationStatus(pending)).isEqualTo("CANCELED");
        assertThat(cancelReason(pending)).isEqualTo("SANCTIONED");
        Map<String, Object> notification = jdbc.queryForMap(
                "SELECT body, link FROM notification WHERE member_id = ? AND type = 'PROGRAM_APPLICATION_CANCELED'",
                target);
        assertThat(notification.get("body")).isEqualTo("이용 제재로 행사 신청이 취소됐습니다.");
        assertThat(notification.get("link")).isEqualTo("/programs/" + programId);
    }

    private String cancelReason(long applicationId) {
        return jdbc.queryForObject(
                "SELECT cancel_reason FROM program_application WHERE id = ?", String.class, applicationId);
    }

    private LocalDateTime reviewRequestedAt(long applicationId) {
        return jdbc.queryForObject(
                "SELECT review_requested_at FROM program_application WHERE id = ?", LocalDateTime.class, applicationId);
    }

    private void assertCanceledPayload(long applicationId, long memberId, long programId) {
        String payload = jdbc.queryForObject(
                "SELECT payload FROM outbox_event WHERE event_type = 'PROGRAM_APPLICATION_CANCELED' AND aggregate_id = ?",
                String.class,
                applicationId);
        assertThat(JsonPath.<Number>read(payload, "$.memberId").longValue()).isEqualTo(memberId);
        assertThat(JsonPath.<Number>read(payload, "$.programId").longValue()).isEqualTo(programId);
        assertThat(JsonPath.<String>read(payload, "$.reason")).isEqualTo("SANCTIONED");
        assertThat(JsonPath.<Boolean>read(payload, "$.refunded")).isFalse();
    }
}
