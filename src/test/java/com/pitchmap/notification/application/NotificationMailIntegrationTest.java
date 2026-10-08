package com.pitchmap.notification.application;

import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.mail.MailMessage;
import com.pitchmap.common.mail.TestMailSender;
import com.pitchmap.common.outbox.OutboxEventRecorder;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.infra.MemberJpaRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class NotificationMailIntegrationTest {

    private static final String AGGREGATE_TYPE = "BASECAMP";
    private static final String APPROVED = "BASECAMP_APPROVED";
    private static final String REJECTED = "BASECAMP_REJECTED";
    private static final String CONFIRMED = "BASECAMP_CONFIRMED";

    @Autowired
    private OutboxPublisher publisher;

    @Autowired
    private OutboxEventRecorder recorder;

    @Autowired
    private OutboxProperties properties;

    @Autowired
    private TestMailSender mailSender;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    @BeforeEach
    void resetMailSender() {
        mailSender.reset();
    }

    @Test
    @DisplayName("[F-20] 설정이 없으면 기본값대로 메일을 한 통 보내고 보낸 시각을 기록한다")
    void sendsMailByDefaultAndRecordsSentAt() {
        // given
        Member applicant = saveActiveMember();
        recordApproved(applicant.getId(), 3L);

        // when
        publisher.publishPending();

        // then
        assertThat(mailSender.sent()).hasSize(1);
        MailMessage mail = mailSender.sent().get(0);
        assertThat(mail.to()).isEqualTo(applicant.getEmail());
        assertThat(mail.subject()).isEqualTo("[피치맵] 합류 승인");
        assertThat(mail.body()).contains("/basecamps/3");
        assertThat(countSentNotifications()).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-20] 이메일을 끈 종류는 알림함에만 쌓이고 메일은 없으며, 다른 종류는 여전히 보낸다")
    void turnedOffTypeIsInboxOnly() {
        // given
        Member applicant = saveActiveMember();
        disableEmail(applicant.getId(), APPROVED);
        recordApproved(applicant.getId(), 3L);
        recordRejected(applicant.getId(), 4L);

        // when
        publisher.publishPending();

        // then
        assertThat(countNotifications()).isEqualTo(2);
        assertThat(mailSender.sent()).hasSize(1);
        assertThat(mailSender.sent().get(0).subject()).isEqualTo("[피치맵] 합류 거절");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT email_sent_at IS NULL FROM notification WHERE type = ?", Boolean.class, APPROVED))
                .isTrue();
    }

    @Test
    @DisplayName("[F-20] 받는 사람이 여럿인 이벤트에서 이메일을 끈 회원만 빼고 나머지에게 보낸다")
    void multiRecipientSkipsOnlyDisabledMember() {
        // given
        Member first = saveActiveMember();
        Member second = saveActiveMember();
        Member third = saveActiveMember();
        disableEmail(second.getId(), CONFIRMED);
        recordConfirmed(3L, List.of(first.getId(), second.getId(), third.getId()));

        // when
        publisher.publishPending();

        // then
        assertThat(countNotifications()).isEqualTo(3);
        assertThat(mailSender.sent())
                .extracting(MailMessage::to)
                .containsExactlyInAnyOrder(first.getEmail(), third.getEmail());
    }

    @Test
    @DisplayName("[F-20] 이메일 인증 전(UNVERIFIED) 회원에게는 알림만 만들고 메일은 보내지 않는다")
    void unverifiedMemberGetsNoMail() {
        // given
        Member unverified = memberRepository.saveAndFlush(aMember().build());
        recordApproved(unverified.getId(), 3L);

        // when
        publisher.publishPending();

        // then
        assertThat(countNotifications()).isEqualTo(1);
        assertThat(mailSender.sent()).isEmpty();
    }

    @Test
    @DisplayName("[F-20] 이메일 인증을 마친 정지(SUSPENDED) 회원에게는 제재 확정 알림 메일을 보낸다")
    void verifiedSuspendedMemberGetsMail() {
        // given
        Member suspended = saveActiveMember();
        jdbcTemplate.update("UPDATE member SET status = 'SUSPENDED' WHERE id = ?", suspended.getId());
        recordSanctionConfirmed(suspended.getId());

        // when
        publisher.publishPending();

        // then
        assertThat(mailSender.sent()).hasSize(1);
        assertThat(mailSender.sent().get(0).to()).isEqualTo(suspended.getEmail());
    }

    @Test
    @DisplayName("[F-20] 이메일 인증을 마치지 않은 정지(SUSPENDED) 회원에게는 알림만 만들고 메일은 보내지 않는다")
    void unverifiedSuspendedMemberGetsNoMail() {
        // given
        Member suspended = memberRepository.saveAndFlush(aMember().build());
        jdbcTemplate.update("UPDATE member SET status = 'SUSPENDED' WHERE id = ?", suspended.getId());
        recordSanctionConfirmed(suspended.getId());

        // when
        publisher.publishPending();

        // then
        assertThat(countNotifications()).isEqualTo(1);
        assertThat(mailSender.sent()).isEmpty();
    }

    @Test
    @DisplayName("[F-20][NFR-04] 메일 발송이 한 번 실패하면 이벤트를 다시 처리하고, 받는 회원마다 메일은 한 통이며 알림은 중복되지 않는다")
    void failedSendIsRetriedWithoutDuplicates() {
        // given
        Member first = saveActiveMember();
        Member second = saveActiveMember();
        recordConfirmed(3L, List.of(first.getId(), second.getId()));
        mailSender.failNextSends(1);

        // when
        publisher.publishPending();

        // then
        assertThat(eventStatus()).isEqualTo("PENDING");
        assertThat(countNotifications()).isEqualTo(2);
        assertThat(mailSender.sent()).hasSize(1);
        assertThat(countSentNotifications()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT last_error FROM outbox_event", String.class))
                .contains("MailDeliveryException")
                .doesNotContain("@");

        // when
        clock.advance(properties.retryDelay());
        publisher.publishPending();

        // then
        assertThat(eventStatus()).isEqualTo("PUBLISHED");
        assertThat(countNotifications()).isEqualTo(2);
        assertThat(mailSender.sent())
                .extracting(MailMessage::to)
                .containsExactlyInAnyOrder(first.getEmail(), second.getEmail());
        assertThat(countSentNotifications()).isEqualTo(2);
    }

    @Test
    @DisplayName("[F-20] 재시도 사이에 회원이 이메일을 끄면 남은 메일은 보내지 않는다")
    void settingChangedBetweenRetriesIsRespected() {
        // given
        Member applicant = saveActiveMember();
        recordApproved(applicant.getId(), 3L);
        mailSender.failNextSends(1);
        publisher.publishPending();

        // when
        disableEmail(applicant.getId(), APPROVED);
        clock.advance(properties.retryDelay());
        publisher.publishPending();

        // then
        assertThat(eventStatus()).isEqualTo("PUBLISHED");
        assertThat(mailSender.sent()).isEmpty();
    }

    private Member saveActiveMember() {
        Member member = memberRepository.saveAndFlush(aMember().build());
        jdbcTemplate.update(
                "UPDATE member SET status = 'ACTIVE', email_verified_at = updated_at WHERE id = ?", member.getId());
        return member;
    }

    private void disableEmail(long memberId, String type) {
        jdbcTemplate.update(
                "INSERT INTO notification_setting (member_id, type, email_enabled) VALUES (?, ?, FALSE)",
                memberId,
                type);
    }

    private void recordApproved(long applicantId, long basecampId) {
        record(APPROVED, basecampId, Map.of("applicationId", 1L, "applicantId", applicantId, "basecampId", basecampId));
    }

    private void recordSanctionConfirmed(long memberId) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("memberId", memberId);
        payload.put("sanctionId", 1L);
        payload.put("sanctionType", "PERMANENT");
        payload.put("endsAt", null);
        record("SANCTION_CONFIRMED", memberId, payload);
    }

    private void recordRejected(long applicantId, long basecampId) {
        record(REJECTED, basecampId, Map.of("applicationId", 2L, "applicantId", applicantId, "basecampId", basecampId));
    }

    private void recordConfirmed(long basecampId, List<Long> memberIds) {
        record(CONFIRMED, basecampId, Map.of("basecampId", basecampId, "memberIds", memberIds));
    }

    private void record(String eventType, long aggregateId, Map<String, Object> payload) {
        transactionTemplate.executeWithoutResult(
                status -> recorder.record(eventType, AGGREGATE_TYPE, aggregateId, payload));
    }

    private int countNotifications() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification", Integer.class);
    }

    private int countSentNotifications() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE email_sent_at IS NOT NULL", Integer.class);
    }

    private String eventStatus() {
        return jdbcTemplate.queryForObject("SELECT status FROM outbox_event", String.class);
    }
}
