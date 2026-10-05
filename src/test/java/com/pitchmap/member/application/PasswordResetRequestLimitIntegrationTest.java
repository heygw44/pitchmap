package com.pitchmap.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.error.RateLimitedException;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.domain.MemberBuilder;
import com.pitchmap.member.domain.MemberErrorCode;
import com.pitchmap.member.domain.PasswordResetRequestPolicy;
import com.pitchmap.member.domain.ResetToken;
import com.pitchmap.member.infra.MemberJpaRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class PasswordResetRequestLimitIntegrationTest {

    private static final String CLIENT_IP = "203.0.113.7";
    private static final String OTHER_IP = "198.51.100.9";
    private static final Duration INTERVAL = Duration.ofSeconds(60);

    @Autowired
    private PasswordResetRequestService requestService;

    @Autowired
    private MutableClock clock;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("[F-02][PW-05] 가입된 이메일과 없는 이메일에 같은 순서로 요청하면 통과와 한도의 순서가 같다")
    void registeredAndUnknownEmailGetSameOutcomeSequence() {
        // given
        Member member = newMember(TestSequence.email());
        String unknownEmail = TestSequence.email();

        // when
        List<Outcome> registered = runSequence(member.getEmail(), CLIENT_IP);
        List<Outcome> unknown = runSequence(unknownEmail, OTHER_IP);

        // then
        assertThat(registered).containsExactly(Outcome.PASSED, Outcome.LIMITED, Outcome.PASSED, Outcome.LIMITED);
        assertThat(unknown).isEqualTo(registered);
    }

    @Test
    @DisplayName("[F-02][PW-05] 한도 예외에는 Retry-After로 쓸 양수 시간이 있다. 간격에 걸리면 남은 간격, 하루 한도에 걸리면 구간 끝까지다")
    void limitedExceptionCarriesPositiveWaitTime() {
        // given
        String email = TestSequence.email();
        Instant start = clock.instant();
        requestService.request(email, CLIENT_IP);
        clock.advance(Duration.ofSeconds(20));

        // when
        RateLimitedException byInterval = expectLimited(email, CLIENT_IP);

        // then
        assertThat(byInterval.getErrorCode()).isEqualTo(MemberErrorCode.PASSWORD_RESET_LIMITED);
        assertThat(byInterval.retryAfter()).isEqualTo(Duration.ofSeconds(40));

        // given
        clock.setInstant(start.plus(INTERVAL));
        requestFourMore(email, CLIENT_IP);
        clock.advance(INTERVAL);

        // when
        RateLimitedException byWindow = expectLimited(email, CLIENT_IP);

        // then
        Instant windowEnd = start.plus(PasswordResetRequestPolicy.EMAIL.window());
        assertThat(byWindow.retryAfter()).isPositive().isEqualTo(Duration.between(clock.instant(), windowEnd));
        assertThat(byWindow.retryAfter()).isGreaterThan(INTERVAL);
    }

    @Test
    @DisplayName("[F-02][PW-05] 60초가 지나면 통과하고, 24시간에 5번을 넘기면 막히고, 구간이 끝나는 정각에 풀린다")
    void intervalAndDailyLimitReleaseOverTime() {
        // given
        String email = TestSequence.email();
        Instant start = clock.instant();
        requestService.request(email, CLIENT_IP);
        clock.setInstant(start.plus(INTERVAL).minusSeconds(1));
        assertThat(attempt(email, CLIENT_IP)).isEqualTo(Outcome.LIMITED);

        // when
        clock.setInstant(start.plus(INTERVAL));
        Outcome afterInterval = attempt(email, CLIENT_IP);

        // then
        assertThat(afterInterval).isEqualTo(Outcome.PASSED);

        // given
        requestThreeMoreAfterSecond(email, CLIENT_IP);
        clock.advance(INTERVAL);

        // when
        Outcome sixth = attempt(email, CLIENT_IP);
        clock.setInstant(start.plus(PasswordResetRequestPolicy.EMAIL.window()).minusSeconds(1));
        Outcome justBeforeEnd = attempt(email, CLIENT_IP);
        clock.setInstant(start.plus(PasswordResetRequestPolicy.EMAIL.window()));
        Outcome atEnd = attempt(email, CLIENT_IP);

        // then
        assertThat(sixth).isEqualTo(Outcome.LIMITED);
        assertThat(justBeforeEnd).isEqualTo(Outcome.LIMITED);
        assertThat(atEnd).isEqualTo(Outcome.PASSED);
        assertThat(requestCountOf(PasswordResetRequestPolicy.emailKey(email))).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-02][PW-05] 한도에 걸린 요청은 이벤트도 IP·이메일 횟수도 늘리지 않는다")
    void limitedRequestChangesNothing() {
        // given
        Member member = newMember(TestSequence.email());
        requestService.request(member.getEmail(), CLIENT_IP);
        clock.advance(Duration.ofSeconds(10));
        List<Map<String, Object>> rowsBefore = throttleRows();
        int eventsBefore = countRows("outbox_event");

        // when
        Outcome outcome = attempt(member.getEmail(), CLIENT_IP);

        // then
        assertThat(outcome).isEqualTo(Outcome.LIMITED);
        assertThat(throttleRows()).isEqualTo(rowsBefore);
        assertThat(countRows("outbox_event")).isEqualTo(eventsBefore).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-02][PW-05] 같은 IP로 서로 다른 이메일 21개를 보내면 21번째가 막히고 IP 횟수는 20이다")
    void ipLimitBlocksTwentyFirstEmail() {
        // when
        List<Outcome> outcomes = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            outcomes.add(attempt(TestSequence.email(), CLIENT_IP));
        }

        // then
        assertThat(outcomes.subList(0, 20)).containsOnly(Outcome.PASSED);
        assertThat(outcomes.get(20)).isEqualTo(Outcome.LIMITED);
        assertThat(requestCountOf(PasswordResetRequestPolicy.ipKey(CLIENT_IP))).isEqualTo(20);
        assertThat(countRows("password_reset_throttle")).isEqualTo(21);
    }

    @Test
    @DisplayName("[F-02][PW-05] 이메일 행이 막아서 던질 때 IP 행의 횟수는 늘지 않고, 새 IP의 행도 남지 않는다")
    void emailBlockDoesNotCountAgainstIp() {
        // given
        String email = TestSequence.email();
        requestService.request(email, CLIENT_IP);
        clock.advance(Duration.ofSeconds(10));

        // when
        Outcome sameIp = attempt(email, CLIENT_IP);
        Outcome newIp = attempt(email, OTHER_IP);

        // then
        assertThat(sameIp).isEqualTo(Outcome.LIMITED);
        assertThat(newIp).isEqualTo(Outcome.LIMITED);
        assertThat(requestCountOf(PasswordResetRequestPolicy.ipKey(CLIENT_IP))).isEqualTo(1);
        assertThat(requestCountOf(PasswordResetRequestPolicy.ipKey(OTHER_IP))).isNull();
    }

    @Test
    @DisplayName("[F-02][PW-05] 테이블에는 원문 이메일과 IP가 없고, 키는 접두사를 붙인 64자 해시다")
    void tableKeepsOnlyHashedKeys() {
        // given
        String email = "Hashed.Only@Example.com";

        // when
        requestService.request(email, CLIENT_IP);

        // then
        List<String> keys = jdbc.queryForList("SELECT throttle_key FROM password_reset_throttle", String.class);
        assertThat(keys)
                .containsExactlyInAnyOrder(
                        ResetToken.hash("email:hashed.only@example.com"), ResetToken.hash("ip:" + CLIENT_IP))
                .allSatisfy(key -> assertThat(key).matches("[0-9a-f]{64}"));
        String dump = jdbc.queryForList("SELECT * FROM password_reset_throttle").toString();
        assertThat(dump)
                .doesNotContain("hashed.only")
                .doesNotContain("Hashed.Only")
                .doesNotContain(CLIENT_IP);
    }

    @Test
    @DisplayName("[F-02][PW-05] 요청 트랜잭션이 롤백되면 한도 행도 이벤트도 남지 않는다")
    void rolledBackRequestLeavesNoRowsOrEvents() {
        // given
        Member member = newMember(TestSequence.email());

        // when
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                    requestService.request(member.getEmail(), CLIENT_IP);
                    throw new IllegalStateException("요청 이후 단계 실패");
                }))
                .isInstanceOf(IllegalStateException.class);

        // then
        assertThat(countRows("password_reset_throttle")).isZero();
        assertThat(countRows("outbox_event")).isZero();
    }

    @Test
    @DisplayName("[F-02][PW-05] 대소문자가 달라도 같은 이메일로 센다")
    void emailCaseDoesNotChangeTheLimit() {
        // given
        requestService.request("Mixed.Case@Example.com", CLIENT_IP);
        clock.advance(Duration.ofSeconds(10));

        // when
        Outcome outcome = attempt("MIXED.CASE@EXAMPLE.COM", CLIENT_IP);

        // then
        assertThat(outcome).isEqualTo(Outcome.LIMITED);
        assertThat(requestCountOf(PasswordResetRequestPolicy.emailKey("mixed.case@example.com")))
                .isEqualTo(1);
        assertThat(countRows("password_reset_throttle")).isEqualTo(2);
    }

    @Test
    @DisplayName("[F-02][PW-05] 탈퇴한 회원은 이벤트가 없지만 한도는 똑같이 센다")
    void withdrawnMemberIsCountedWithoutEvent() {
        // given
        Member member = newMember(TestSequence.email());
        jdbc.update("UPDATE member SET status = 'WITHDRAWN' WHERE id = ?", member.getId());

        // when
        Outcome first = attempt(member.getEmail(), CLIENT_IP);
        Outcome second = attempt(member.getEmail(), CLIENT_IP);

        // then
        assertThat(first).isEqualTo(Outcome.PASSED);
        assertThat(second).isEqualTo(Outcome.LIMITED);
        assertThat(countRows("outbox_event")).isZero();
        assertThat(requestCountOf(PasswordResetRequestPolicy.emailKey(member.getEmail())))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("[F-02][PW-05] 한도를 통과하면 가입된 이메일에만 이벤트가 1건 생긴다")
    void passedRequestStillRecordsEventOnlyForRegisteredEmail() {
        // given
        Member member = newMember(TestSequence.email());

        // when
        requestService.request(TestSequence.email(), CLIENT_IP);

        // then
        assertThat(countRows("outbox_event")).isZero();

        // when
        requestService.request(member.getEmail(), CLIENT_IP);

        // then
        Map<String, Object> event = jdbc.queryForMap("SELECT * FROM outbox_event");
        assertThat(event.get("event_type")).isEqualTo("PASSWORD_RESET_REQUESTED");
        assertThat(event.get("aggregate_id")).isEqualTo(member.getId());
    }

    @Test
    @DisplayName("[F-02][PW-05] 요청 IP가 비어 있으면 IllegalArgumentException이고 아무것도 남지 않는다")
    void blankRequestIpIsRejected() {
        assertThatThrownBy(() -> requestService.request(TestSequence.email(), " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> requestService.request(TestSequence.email(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(countRows("password_reset_throttle")).isZero();
    }

    // 같은 호출 순서(요청, 곧바로 요청, 60초 뒤 요청, 곧바로 요청)를 주고 결과를 모은 뒤 시계를 처음 시각으로 되돌린다.
    private List<Outcome> runSequence(String email, String ip) {
        Instant start = clock.instant();
        List<Outcome> outcomes = new ArrayList<>();
        outcomes.add(attempt(email, ip));
        outcomes.add(attempt(email, ip));
        clock.advance(INTERVAL);
        outcomes.add(attempt(email, ip));
        outcomes.add(attempt(email, ip));
        clock.setInstant(start);
        return outcomes;
    }

    // 간격(60초)을 지키며 4번 더 요청해서 이메일 구간의 횟수를 5로 채운다. 마지막 요청은 시계를 움직이지 않고 끝낸다.
    private void requestFourMore(String email, String ip) {
        for (int i = 0; i < 4; i++) {
            requestService.request(email, ip);
            if (i < 3) {
                clock.advance(INTERVAL);
            }
        }
    }

    // 시계가 두 번째 요청이 통과한 시각에 있다고 보고, 3·4·5번째 요청을 간격대로 보내 횟수를 5로 채운다.
    private void requestThreeMoreAfterSecond(String email, String ip) {
        for (int i = 0; i < 3; i++) {
            clock.advance(INTERVAL);
            requestService.request(email, ip);
        }
    }

    private Outcome attempt(String email, String ip) {
        try {
            requestService.request(email, ip);
            return Outcome.PASSED;
        } catch (RateLimitedException e) {
            assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.PASSWORD_RESET_LIMITED);
            return Outcome.LIMITED;
        }
    }

    private RateLimitedException expectLimited(String email, String ip) {
        try {
            requestService.request(email, ip);
        } catch (RateLimitedException e) {
            return e;
        }
        throw new AssertionError("요청이 한도에 걸리지 않았습니다.");
    }

    private Member newMember(String email) {
        return memberRepository.saveAndFlush(
                MemberBuilder.aMember().email(email).now(clock.instant()).build());
    }

    private List<Map<String, Object>> throttleRows() {
        return jdbc.queryForList("SELECT * FROM password_reset_throttle ORDER BY throttle_key");
    }

    private Integer requestCountOf(String key) {
        List<Integer> counts = jdbc.queryForList(
                "SELECT request_count FROM password_reset_throttle WHERE throttle_key = ?", Integer.class, key);
        return counts.isEmpty() ? null : counts.get(0);
    }

    private int countRows(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private enum Outcome {
        PASSED,
        LIMITED
    }
}
