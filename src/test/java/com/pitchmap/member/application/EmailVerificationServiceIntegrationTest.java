package com.pitchmap.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.error.RateLimitedException;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.domain.MemberBuilder;
import com.pitchmap.member.domain.MemberErrorCode;
import com.pitchmap.member.domain.MemberStatus;
import com.pitchmap.member.domain.VerificationCode;
import com.pitchmap.member.infra.MemberJpaRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class EmailVerificationServiceIntegrationTest {

    private static final String IP = "203.0.113.7";
    private static final String OTHER_IP = "203.0.113.8";
    private static final String EVENT_TYPE = "EMAIL_VERIFICATION_REQUESTED";

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName("[F-01][EV-01] 발급한 코드를 입력하면 회원이 ACTIVE가 되고 코드 행에 인증 시각이 남는다")
    void validCodeActivatesMember() {
        // given
        Member member = newMember();
        String code = issue(member);

        // when
        VerificationResult result = emailVerificationService.verify(member.getId(), code);

        // then
        assertThat(result.status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(statusOf(member)).isEqualTo("ACTIVE");
        assertThat(queryInstant("SELECT email_verified_at FROM member WHERE id = ?", member.getId()))
                .isEqualTo(clock.instant());
        assertThat(jdbc.queryForObject(
                        "SELECT verified_at IS NOT NULL FROM email_verification WHERE member_id = ?",
                        Boolean.class,
                        member.getId()))
                .isTrue();
    }

    @Test
    @DisplayName("[F-01][EV-01] 코드 원값은 저장하지 않고 회원 ID와 코드의 해시와 요청 IP, 10분 뒤 만료 시각만 저장한다")
    void storesOnlyHashOfCode() {
        // given
        Member member = newMember();

        // when
        String code = issue(member);

        // then
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT code_hash, request_ip, attempt_count FROM email_verification WHERE member_id = ?",
                member.getId());
        assertThat(row.get("code_hash")).isEqualTo(VerificationCode.hash(member.getId(), code));
        assertThat(row.get("code_hash")).isNotEqualTo(code);
        assertThat(row.get("request_ip")).isEqualTo(IP);
        assertThat(((Number) row.get("attempt_count")).intValue()).isZero();
        assertThat(queryInstant("SELECT expires_at FROM email_verification WHERE member_id = ?", member.getId()))
                .isEqualTo(clock.instant().plus(Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("[F-01][EV-01] 인증을 마쳤거나 없는 회원에게는 코드를 발급하지 않는다")
    void doesNotIssueForNonUnverifiedOrMissingMember() {
        // given
        Member activeMember = newMember();
        emailVerificationService.verify(activeMember.getId(), issue(activeMember));
        long rowsBefore = countVerificationRows();

        // when & then
        assertThat(emailVerificationService.issueFor(activeMember.getId(), IP)).isEmpty();
        assertThat(emailVerificationService.issueFor(Long.MAX_VALUE, IP)).isEmpty();
        assertThat(countVerificationRows()).isEqualTo(rowsBefore);
    }

    @Test
    @DisplayName("[F-01][EV-01] 만료 10분의 1초 전까지는 인증되고 정각부터는 EMAIL_CODE_EXPIRED다")
    void codeIsValidUntilTenMinutesAfterIssue() {
        // given
        Member justBefore = newMember();
        String codeJustBefore = issue(justBefore);
        Member atBoundary = newMember();
        String codeAtBoundary = issue(atBoundary);

        // when & then: 10분에서 1초 모자란 시각
        clock.advance(Duration.ofMinutes(10).minusSeconds(1));
        assertThat(emailVerificationService
                        .verify(justBefore.getId(), codeJustBefore)
                        .status())
                .isEqualTo(MemberStatus.ACTIVE);

        // when & then: 정각
        clock.advance(Duration.ofSeconds(1));
        assertThatThrownBy(() -> emailVerificationService.verify(atBoundary.getId(), codeAtBoundary))
                .hasFieldOrPropertyWithValue("errorCode", MemberErrorCode.EMAIL_CODE_EXPIRED);
        assertThat(statusOf(atBoundary)).isEqualTo("UNVERIFIED");
    }

    @Test
    @DisplayName("[F-01][EV-01] 만료된 코드는 맞아도 시도 횟수를 올리지 않고 거부한다")
    void expiredCodeIsRejectedWithoutCountingAttempt() {
        // given
        Member member = newMember();
        String code = issue(member);
        clock.advance(Duration.ofMinutes(11));

        // when & then
        assertThatThrownBy(() -> emailVerificationService.verify(member.getId(), code))
                .hasFieldOrPropertyWithValue("errorCode", MemberErrorCode.EMAIL_CODE_EXPIRED);
        assertThatThrownBy(() -> emailVerificationService.verify(member.getId(), wrongCodeFor(code)))
                .hasFieldOrPropertyWithValue("errorCode", MemberErrorCode.EMAIL_CODE_EXPIRED);
        assertThat(latestAttemptCount(member)).isZero();
    }

    @Test
    @DisplayName("[F-01][EV-02] 틀린 횟수가 예외 뒤에도 저장되고, 5번 틀리면 이후에는 맞는 코드도 EMAIL_CODE_ATTEMPTS_EXCEEDED다")
    void fiveWrongAttemptsInvalidateCodeEvenForCorrectCode() {
        // given
        Member member = newMember();
        String code = issue(member);
        String wrongCode = wrongCodeFor(code);

        // when & then: 다섯 번째까지는 INVALID이고 횟수가 예외와 상관없이 남는다
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThatThrownBy(() -> emailVerificationService.verify(member.getId(), wrongCode))
                    .hasFieldOrPropertyWithValue("errorCode", MemberErrorCode.EMAIL_CODE_INVALID);
            assertThat(latestAttemptCount(member)).isEqualTo(attempt);
        }

        // when & then: 여섯 번째부터는 맞는 코드든 틀린 코드든 EXCEEDED이고 횟수는 5로 멈춘다
        assertThatThrownBy(() -> emailVerificationService.verify(member.getId(), wrongCode))
                .hasFieldOrPropertyWithValue("errorCode", MemberErrorCode.EMAIL_CODE_ATTEMPTS_EXCEEDED);
        assertThatThrownBy(() -> emailVerificationService.verify(member.getId(), code))
                .hasFieldOrPropertyWithValue("errorCode", MemberErrorCode.EMAIL_CODE_ATTEMPTS_EXCEEDED);
        assertThat(latestAttemptCount(member)).isEqualTo(5);
        assertThat(statusOf(member)).isEqualTo("UNVERIFIED");
    }

    @Test
    @DisplayName("[F-01][EV-02] 4번 틀린 뒤 맞는 코드를 입력하면 인증된다")
    void correctCodeAfterFourWrongAttemptsSucceeds() {
        // given
        Member member = newMember();
        String code = issue(member);
        for (int attempt = 0; attempt < 4; attempt++) {
            assertThatThrownBy(() -> emailVerificationService.verify(member.getId(), wrongCodeFor(code)))
                    .hasFieldOrPropertyWithValue("errorCode", MemberErrorCode.EMAIL_CODE_INVALID);
        }

        // when
        VerificationResult result = emailVerificationService.verify(member.getId(), code);

        // then
        assertThat(result.status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(statusOf(member)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("[F-01][EV-02] 숫자 6자리가 아닌 입력은 EMAIL_CODE_INVALID이고 시도 횟수를 올리지 않는다")
    void malformedInputIsNotCountedAsAttempt() {
        // given
        Member member = newMember();
        issue(member);

        // when & then
        for (String malformed : new String[] {"12345", "1234567", "abcdef", " 12345", "", null}) {
            assertThatThrownBy(() -> emailVerificationService.verify(member.getId(), malformed))
                    .hasFieldOrPropertyWithValue("errorCode", MemberErrorCode.EMAIL_CODE_INVALID);
        }
        assertThat(latestAttemptCount(member)).isZero();
    }

    @Test
    @DisplayName("[F-01][EV-02] 발급한 코드가 없으면 EMAIL_CODE_INVALID다")
    void verifyWithoutIssuedCodeIsInvalid() {
        // given
        Member member = newMember();

        // when & then
        assertThatThrownBy(() -> emailVerificationService.verify(member.getId(), "123456"))
                .hasFieldOrPropertyWithValue("errorCode", MemberErrorCode.EMAIL_CODE_INVALID);
    }

    @Test
    @DisplayName("[F-01][EV-02] 이미 인증한 회원이 코드를 다시 입력하면 EMAIL_ALREADY_VERIFIED다")
    void verifyAfterVerifiedIsAlreadyVerified() {
        // given
        Member member = newMember();
        String code = issue(member);
        emailVerificationService.verify(member.getId(), code);

        // when & then
        assertThatThrownBy(() -> emailVerificationService.verify(member.getId(), code))
                .hasFieldOrPropertyWithValue("errorCode", MemberErrorCode.EMAIL_ALREADY_VERIFIED);
        assertThatThrownBy(() -> emailVerificationService.verify(member.getId(), wrongCodeFor(code)))
                .hasFieldOrPropertyWithValue("errorCode", MemberErrorCode.EMAIL_ALREADY_VERIFIED);
    }

    @Test
    @DisplayName("[F-01][EV-01] 코드를 다시 발급하면 가장 최근 코드만 유효하고 이전 코드는 틀린 코드로 센다")
    void onlyLatestIssuedCodeIsValid() {
        // given
        Member member = newMember();
        String firstCode = issue(member);
        String secondCode = issueDifferentFrom(member, firstCode);

        // when & then
        assertThatThrownBy(() -> emailVerificationService.verify(member.getId(), firstCode))
                .hasFieldOrPropertyWithValue("errorCode", MemberErrorCode.EMAIL_CODE_INVALID);
        assertThat(attemptCounts(member)).containsExactly(0, 1);
        assertThat(emailVerificationService.verify(member.getId(), secondCode).status())
                .isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("[F-01][EV-03] 재발송을 요청하면 발송 요청 이벤트가 PENDING으로 한 건 기록되고 코드는 아직 만들지 않는다")
    void resendRecordsPendingEventWithoutCreatingCode() {
        // given
        Member member = newMember();

        // when
        emailVerificationService.requestResend(member.getId(), IP);

        // then
        List<Map<String, Object>> events = jdbc.queryForList(
                "SELECT status, aggregate_type, JSON_UNQUOTE(JSON_EXTRACT(payload, '$.requestIp')) AS request_ip"
                        + " FROM outbox_event WHERE event_type = ? AND aggregate_id = ?",
                EVENT_TYPE,
                member.getId());
        assertThat(events).hasSize(1);
        assertThat(events.get(0))
                .containsEntry("status", "PENDING")
                .containsEntry("aggregate_type", "MEMBER")
                .containsEntry("request_ip", IP);
        assertThat(countVerificationRows()).isZero();
    }

    @Test
    @DisplayName("[F-01][EV-03] 처리하지 않은 발송 요청이 있으면 60초 동안 EMAIL_RESEND_LIMITED이고 이벤트를 더 쌓지 않는다")
    void pendingEventBlocksResend() {
        // given
        Member member = newMember();
        emailVerificationService.requestResend(member.getId(), IP);

        // when & then
        assertResendLimited(member, IP, Duration.ofSeconds(60));
        assertThat(countEvents(member)).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-01][EV-03] 이미 발행된 이벤트만 있으면 재발송을 막지 않는다")
    void publishedEventDoesNotBlockResend() {
        // given
        Member member = newMember();
        emailVerificationService.requestResend(member.getId(), IP);
        jdbc.update("UPDATE outbox_event SET status = 'PUBLISHED' WHERE aggregate_id = ?", member.getId());

        // when
        emailVerificationService.requestResend(member.getId(), IP);

        // then
        assertThat(countEvents(member)).isEqualTo(2);
    }

    @Test
    @DisplayName("[F-01][EV-03] 직전 발송 후 59초에는 EMAIL_RESEND_LIMITED이고 60초가 지나면 재발송된다")
    void resendIsAllowedSixtySecondsAfterLastSend() {
        // given
        Member member = newMember();
        issue(member);

        // when & then
        clock.advance(Duration.ofSeconds(59));
        assertResendLimited(member, IP, Duration.ofSeconds(1));
        assertThat(countEvents(member)).isZero();

        clock.advance(Duration.ofSeconds(1));
        emailVerificationService.requestResend(member.getId(), IP);
        assertThat(countEvents(member)).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-01][EV-03] 가입 때 보낸 코드를 포함해 24시간에 5번까지만 보내고, 가장 오래된 발송이 24시간 지나면 풀린다")
    void dailyLimitCountsAllSendsIncludingFirstOne() {
        // given: 2분 간격으로 5번 발송(첫 번째가 가입 때 보낸 코드)
        Member member = newMember();
        Instant firstSentAt = clock.instant();
        for (int sent = 0; sent < 5; sent++) {
            issue(member);
            clock.advance(Duration.ofMinutes(2));
        }

        // when & then: 10분이 지났어도 하루 한도에 걸려 가장 오래된 발송이 풀릴 때까지 남은 시간을 알려 준다
        assertResendLimited(member, IP, Duration.ofHours(24).minusMinutes(10));

        // when & then: 24시간에서 1초 모자라면 아직 막혀 있다
        clock.setInstant(firstSentAt.plus(Duration.ofHours(24)).minusSeconds(1));
        assertResendLimited(member, IP, Duration.ofSeconds(1));

        // when & then: 첫 발송이 24시간 지나면 재발송된다
        clock.setInstant(firstSentAt.plus(Duration.ofHours(24)));
        emailVerificationService.requestResend(member.getId(), IP);
        assertThat(countEvents(member)).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-01][EV-03] 4번 보낸 회원은 하루 한도에 걸리지 않는다")
    void fourSendsInDayDoNotHitDailyLimit() {
        // given
        Member member = newMember();
        for (int sent = 0; sent < 4; sent++) {
            issue(member);
            clock.advance(Duration.ofMinutes(2));
        }

        // when
        emailVerificationService.requestResend(member.getId(), IP);

        // then
        assertThat(countEvents(member)).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-01][EV-03] 같은 IP가 1시간에 20번 요청하면 다른 회원도 막고, 1시간이 지나면 풀린다")
    void ipLimitBlocksOtherMembersFromSameIp() {
        // given: 서로 다른 회원 20명이 같은 IP로 코드를 받았다
        for (int sent = 0; sent < 20; sent++) {
            issue(newMember());
        }
        Member another = newMember();

        // when & then: 같은 IP는 막히고 다른 IP는 통과한다
        assertResendLimited(another, IP, Duration.ofHours(1));
        emailVerificationService.requestResend(another.getId(), OTHER_IP);
        assertThat(countEvents(another)).isEqualTo(1);

        // when & then: 1시간에서 1초 모자라면 아직 막혀 있고, 1시간이 지나면 풀린다
        Member later = newMember();
        clock.advance(Duration.ofHours(1).minusSeconds(1));
        assertResendLimited(later, IP, Duration.ofSeconds(1));
        clock.advance(Duration.ofSeconds(1));
        emailVerificationService.requestResend(later.getId(), IP);
        assertThat(countEvents(later)).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-01][EV-03] 같은 IP에서 19번 요청받은 상태는 막지 않는다")
    void nineteenSendsFromSameIpDoNotHitIpLimit() {
        // given
        for (int sent = 0; sent < 19; sent++) {
            issue(newMember());
        }
        Member another = newMember();

        // when
        emailVerificationService.requestResend(another.getId(), IP);

        // then
        assertThat(countEvents(another)).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-01][EV-03] 이미 인증한 회원의 재발송 요청은 EMAIL_ALREADY_VERIFIED이고 이벤트를 기록하지 않는다")
    void resendAfterVerifiedIsAlreadyVerified() {
        // given
        Member member = newMember();
        emailVerificationService.verify(member.getId(), issue(member));
        clock.advance(Duration.ofMinutes(5));

        // when & then
        assertThatThrownBy(() -> emailVerificationService.requestResend(member.getId(), IP))
                .hasFieldOrPropertyWithValue("errorCode", MemberErrorCode.EMAIL_ALREADY_VERIFIED);
        assertThat(countEvents(member)).isZero();
    }

    @Test
    @DisplayName("[F-01][EV-03] 재발송을 막을 때 던지는 예외에는 EMAIL_RESEND_LIMITED와 남은 시간이 들어 있다")
    void limitedExceptionCarriesErrorCodeAndRetryAfter() {
        // given
        Member member = newMember();
        issue(member);
        clock.advance(Duration.ofSeconds(20));

        // when & then
        assertThatThrownBy(() -> emailVerificationService.requestResend(member.getId(), IP))
                .isInstanceOfSatisfying(RateLimitedException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.EMAIL_RESEND_LIMITED);
                    assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(40));
                });
    }

    private void assertResendLimited(Member member, String requestIp, Duration expectedRetryAfter) {
        assertThatThrownBy(() -> emailVerificationService.requestResend(member.getId(), requestIp))
                .isInstanceOfSatisfying(RateLimitedException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.EMAIL_RESEND_LIMITED);
                    assertThat(e.retryAfter()).isEqualTo(expectedRetryAfter);
                });
    }

    private Member newMember() {
        return memberRepository.saveAndFlush(
                MemberBuilder.aMember().now(clock.instant()).build());
    }

    private String issue(Member member) {
        return emailVerificationService
                .issueFor(member.getId(), IP)
                .orElseThrow(() -> new AssertionError("미인증 회원인데 코드를 발급하지 않았습니다."))
                .code();
    }

    // 난수가 우연히 같은 코드를 두 번 만들면 이전 코드가 무효가 됐는지 확인할 수 없다.
    // 확률은 100만분의 1이지만 테스트가 흔들리지 않도록, 다른 코드가 나올 때까지 다시 발급한다.
    private String issueDifferentFrom(Member member, String previousCode) {
        String code = issue(member);
        while (code.equals(previousCode)) {
            code = issue(member);
        }
        return code;
    }

    private static String wrongCodeFor(String code) {
        return code.equals("000000") ? "999999" : "000000";
    }

    private int latestAttemptCount(Member member) {
        return jdbc.queryForObject(
                "SELECT attempt_count FROM email_verification WHERE member_id = ? ORDER BY id DESC LIMIT 1",
                Integer.class,
                member.getId());
    }

    private List<Integer> attemptCounts(Member member) {
        return new ArrayList<>(jdbc.queryForList(
                "SELECT attempt_count FROM email_verification WHERE member_id = ? ORDER BY id",
                Integer.class,
                member.getId()));
    }

    private String statusOf(Member member) {
        return jdbc.queryForObject("SELECT status FROM member WHERE id = ?", String.class, member.getId());
    }

    private long countVerificationRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM email_verification", Long.class);
    }

    private long countEvents(Member member) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE event_type = ? AND aggregate_id = ?",
                Long.class,
                EVENT_TYPE,
                member.getId());
    }

    // DATETIME 컬럼에는 UTC 시각이 들어 있으므로(connectionTimeZone=UTC) LocalDateTime을 UTC로 읽어 Instant로 바꾼다.
    private Instant queryInstant(String sql, Object... args) {
        return jdbc.queryForObject(sql, LocalDateTime.class, args).toInstant(ZoneOffset.UTC);
    }
}
