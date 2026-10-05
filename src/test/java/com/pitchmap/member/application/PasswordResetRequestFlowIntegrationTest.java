package com.pitchmap.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.mail.MailMessage;
import com.pitchmap.common.mail.TestMailSender;
import com.pitchmap.common.outbox.OutboxMessage;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.domain.MemberBuilder;
import com.pitchmap.member.domain.PasswordResetPolicy;
import com.pitchmap.member.domain.ResetToken;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.notification.application.OutboxProperties;
import com.pitchmap.notification.application.OutboxPublisher;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class PasswordResetRequestFlowIntegrationTest {

    private static final String MAIL_SUBJECT = "[피치맵] 비밀번호 재설정 안내";
    private static final String LINK_PREFIX = "/password-reset#token=";

    @Autowired
    private PasswordResetRequestService requestService;

    @Autowired
    private PasswordResetRequestedHandler handler;

    @Autowired
    private OutboxPublisher publisher;

    @Autowired
    private OutboxProperties outboxProperties;

    @Autowired
    private TestMailSender mailSender;

    @Autowired
    private MutableClock clock;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetMailSender() {
        mailSender.reset();
    }

    @Test
    @DisplayName("[F-02][PW-02] 가입한 회원이 요청하면 PENDING 이벤트가 생기고, 발행하면 메일 1통과 30분짜리 토큰 행 1개가 생긴다")
    void registeredMemberGetsLinkAndTokenRow() {
        // given
        Member member = newMember("reset-one@example.com");
        Instant issuedAt = clock.instant();

        // when
        requestService.request(member.getEmail());

        // then
        Map<String, Object> event = findOnlyEvent();
        assertThat(event.get("event_type")).isEqualTo("PASSWORD_RESET_REQUESTED");
        assertThat(event.get("aggregate_type")).isEqualTo("MEMBER");
        assertThat(event.get("aggregate_id")).isEqualTo(member.getId());
        assertThat(event.get("status")).isEqualTo("PENDING");
        assertThat(countRows("password_reset_token")).isZero();
        assertThat(mailSender.sent()).isEmpty();

        // when
        int processed = publisher.publishPending();

        // then
        assertThat(processed).isEqualTo(1);
        assertThat(findOnlyEvent().get("status")).isEqualTo("PUBLISHED");
        assertThat(mailSender.sent()).hasSize(1);
        MailMessage mail = mailSender.sent().get(0);
        assertThat(mail.to()).isEqualTo(member.getEmail());
        assertThat(mail.subject()).isEqualTo(MAIL_SUBJECT);
        assertThat(mail.body()).contains(LINK_PREFIX).contains("30분");
        String token = PasswordResetMails.extractToken(mail);
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(mail.body()).contains(LINK_PREFIX + token);
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM password_reset_token");
        assertThat(row.get("member_id")).isEqualTo(member.getId());
        assertThat(row.get("used_at")).isNull();
        assertThat(toInstant(row.get("expires_at"))).isEqualTo(issuedAt.plus(PasswordResetPolicy.TOKEN_VALIDITY));
        assertThat(PasswordResetPolicy.TOKEN_VALIDITY.toMinutes()).isEqualTo(30);
    }

    @Test
    @DisplayName("[F-02][PW-04] 가입하지 않은 이메일은 이벤트도 토큰도 메일도 없고, 가입한 이메일과 똑같이 정상 반환한다")
    void unknownEmailLooksTheSameAndCreatesNothing() {
        // given
        Member member = newMember("known@example.com");

        // when & then
        assertThatCode(() -> requestService.request("unknown@example.com")).doesNotThrowAnyException();
        assertThat(countRows("outbox_event")).isZero();
        assertThat(publisher.publishPending()).isZero();
        assertThat(countRows("password_reset_token")).isZero();
        assertThat(mailSender.sent()).isEmpty();
        assertThatCode(() -> requestService.request(member.getEmail())).doesNotThrowAnyException();
        assertThat(countRows("outbox_event")).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-02][PW-04] 이메일 대소문자가 달라도 같은 회원을 찾는다")
    void emailCaseDoesNotMatter() {
        // given
        Member member = newMember("Mixed@Example.com");
        assertThat(member.getEmail()).isEqualTo("mixed@example.com");

        // when
        requestService.request("MIXED@example.COM");
        publisher.publishPending();

        // then
        assertThat(mailSender.sent()).hasSize(1);
        assertThat(mailSender.sent().get(0).to()).isEqualTo("mixed@example.com");
    }

    @Test
    @DisplayName("[F-02][PW-02] 탈퇴한 회원은 이벤트도 메일도 없고, 미인증·활성·정지 회원은 메일을 1통씩 받는다")
    void onlyWithdrawnMemberGetsNothing() {
        // given
        Member withdrawn = newMember("withdrawn@example.com");
        Member unverified = newMember("unverified@example.com");
        Member active = newMember("active@example.com");
        Member suspended = newMember("suspended@example.com");
        setStatus(withdrawn, "WITHDRAWN");
        setStatus(active, "ACTIVE");
        setStatus(suspended, "SUSPENDED");

        // when
        requestService.request(withdrawn.getEmail());

        // then
        assertThat(countRows("outbox_event")).isZero();

        // when
        requestService.request(unverified.getEmail());
        requestService.request(active.getEmail());
        requestService.request(suspended.getEmail());
        publisher.publishPending();

        // then
        assertThat(mailSender.sent())
                .extracting(MailMessage::to)
                .containsExactlyInAnyOrder(unverified.getEmail(), active.getEmail(), suspended.getEmail());
        assertThat(countRows("password_reset_token")).isEqualTo(3);
    }

    @Test
    @DisplayName("[F-02][PW-02] 처리기 시점에 탈퇴한 회원은 토큰도 메일도 없이 정상 종료한다")
    void memberWithdrawnBeforeHandlingGetsNothing() {
        // given
        Member member = newMember("late-withdrawn@example.com");
        requestService.request(member.getEmail());
        setStatus(member, "WITHDRAWN");

        // when
        int processed = publisher.publishPending();

        // then
        assertThat(processed).isEqualTo(1);
        assertThat(findOnlyEvent().get("status")).isEqualTo("PUBLISHED");
        assertThat(countRows("password_reset_token")).isZero();
        assertThat(mailSender.sent()).isEmpty();
    }

    @Test
    @DisplayName("[F-02][PW-02] 요청 트랜잭션이 롤백되면 이벤트도 토큰도 메일도 남지 않는다")
    void rolledBackRequestLeavesNothing() {
        // given
        Member member = newMember("rollback@example.com");

        // when
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                    requestService.request(member.getEmail());
                    throw new IllegalStateException("요청 이후 단계 실패");
                }))
                .isInstanceOf(IllegalStateException.class);
        int processed = publisher.publishPending();

        // then
        assertThat(countRows("outbox_event")).isZero();
        assertThat(processed).isZero();
        assertThat(countRows("password_reset_token")).isZero();
        assertThat(mailSender.sent()).isEmpty();
    }

    @Test
    @DisplayName("[F-02][PW-02] 토큰 원값은 DB 어디에도 없고 token_hash는 원값의 SHA-256 해시다")
    void rawTokenIsNotStored() {
        // given
        Member member = newMember("hash-only@example.com");
        requestService.request(member.getEmail());

        // when
        publisher.publishPending();

        // then
        String token = PasswordResetMails.extractToken(mailSender.sent().get(0));
        String storedHash = jdbc.queryForObject("SELECT token_hash FROM password_reset_token", String.class);
        assertThat(storedHash).isEqualTo(ResetToken.hash(token)).hasSize(64).doesNotContain(token);
        Map<String, Object> event = findOnlyEvent();
        assertThat(String.valueOf(event.get("payload"))).doesNotContain(token);
        assertThat(String.valueOf(event.get("last_error"))).doesNotContain(token);
    }

    @Test
    @DisplayName("[F-02][PW-02] 메일 발송이 실패하면 이벤트는 PENDING으로 남고, 재시도 간격이 지난 뒤 다시 발송한다")
    void failedMailIsRetriedAfterRetryDelay() {
        // given
        Member member = newMember("retry@example.com");
        requestService.request(member.getEmail());
        mailSender.failNextSends(1);

        // when
        publisher.publishPending();

        // then
        Map<String, Object> afterFailure = findOnlyEvent();
        assertThat(afterFailure.get("status")).isEqualTo("PENDING");
        assertThat(afterFailure.get("attempt_count")).isEqualTo(1);
        assertThat(mailSender.sent()).isEmpty();

        // when
        assertThat(publisher.publishPending()).isZero();
        clock.advance(outboxProperties.retryDelay());
        publisher.publishPending();

        // then
        assertThat(findOnlyEvent().get("status")).isEqualTo("PUBLISHED");
        assertThat(mailSender.sent()).hasSize(1);
        assertThat(countRows("password_reset_token")).isEqualTo(2);
        String sentToken = PasswordResetMails.extractToken(mailSender.sent().get(0));
        List<String> hashes = jdbc.queryForList("SELECT token_hash FROM password_reset_token", String.class);
        assertThat(hashes).contains(ResetToken.hash(sentToken));
    }

    @Test
    @DisplayName("[F-02][PW-02] 같은 이벤트를 처리기가 두 번 처리하면 서로 다른 토큰 행이 둘이고 각 메일의 토큰이 각 행과 맞는다")
    void duplicateDeliveryIssuesTwoIndependentTokens() {
        // given
        Member member = newMember("twice@example.com");
        requestService.request(member.getEmail());
        OutboxMessage message = toMessage(findOnlyEvent());

        // when
        handler.handle(message);
        handler.handle(message);

        // then
        List<MailMessage> mails = mailSender.sent();
        assertThat(mails).hasSize(2);
        String first = PasswordResetMails.extractToken(mails.get(0));
        String second = PasswordResetMails.extractToken(mails.get(1));
        assertThat(first).isNotEqualTo(second);
        List<String> hashes = jdbc.queryForList("SELECT token_hash FROM password_reset_token", String.class);
        assertThat(hashes).containsExactlyInAnyOrder(ResetToken.hash(first), ResetToken.hash(second));
        assertThat(jdbc.queryForList("SELECT used_at FROM password_reset_token WHERE used_at IS NOT NULL"))
                .isEmpty();
    }

    private Member newMember(String email) {
        return memberRepository.saveAndFlush(
                MemberBuilder.aMember().email(email).now(clock.instant()).build());
    }

    private void setStatus(Member member, String status) {
        jdbc.update("UPDATE member SET status = ? WHERE id = ?", status, member.getId());
    }

    private Map<String, Object> findOnlyEvent() {
        return jdbc.queryForMap("SELECT * FROM outbox_event");
    }

    private OutboxMessage toMessage(Map<String, Object> event) {
        return new OutboxMessage(
                ((Number) event.get("id")).longValue(),
                (String) event.get("event_type"),
                (String) event.get("aggregate_type"),
                ((Number) event.get("aggregate_id")).longValue(),
                (String) event.get("payload"),
                clock.instant());
    }

    private int countRows(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private static Instant toInstant(Object dateTime) {
        return ((LocalDateTime) dateTime).toInstant(ZoneOffset.UTC);
    }
}
