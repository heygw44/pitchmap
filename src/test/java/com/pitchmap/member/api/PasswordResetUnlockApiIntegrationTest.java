package com.pitchmap.member.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.mail.TestMailSender;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.application.PasswordResetMails;
import com.pitchmap.member.application.PasswordResetRequestService;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.domain.PasswordResetPolicy;
import com.pitchmap.member.domain.PasswordResetToken;
import com.pitchmap.member.domain.PasswordResetTokenRepository;
import com.pitchmap.member.domain.ResetToken;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.notification.application.OutboxPublisher;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@AutoConfigureMockMvc
class PasswordResetUnlockApiIntegrationTest {

    private static final String OLD_PASSWORD = "Valid-pass1";
    private static final String NEW_PASSWORD = "Newer-pass2";
    private static final String WRONG_PASSWORD = "Wrong-pass1";
    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String CONFIRM_PATH = "/api/auth/password-reset/confirm";
    private static final String LOGIN_LOCKED = "LOGIN_LOCKED";
    private static final String REQUEST_IP = "203.0.113.7";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordResetRequestService requestService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OutboxPublisher publisher;

    @Autowired
    private TestMailSender mailSender;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @BeforeEach
    void resetMailSender() {
        mailSender.reset();
    }

    @Test
    @DisplayName("[F-02][PW-06] 5번 실패해서 잠긴 회원이 재설정을 확인하면 바로 새 비밀번호로 로그인할 수 있다")
    void resetReleasesLockout() {
        Member member = saveMember();
        failLogin(member, 5);
        assertThat(login(member.getEmail(), OLD_PASSWORD)).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        clock.advance(Duration.ofSeconds(1));

        MvcTestResult confirm = confirm(issueToken(member), NEW_PASSWORD);
        MvcTestResult result = login(member.getEmail(), NEW_PASSWORD);

        assertThat(confirm).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(result).hasStatus(HttpStatus.OK);
    }

    @Test
    @DisplayName("[F-02][PW-06] 메일로 받은 토큰으로 재설정해도 잠금이 풀린다")
    void resetThroughMailReleasesLockout() {
        Member member = saveMember();
        failLogin(member, 5);
        clock.advance(Duration.ofSeconds(1));
        requestService.request(member.getEmail(), REQUEST_IP);
        publisher.publishPending();
        assertThat(mailSender.sent()).hasSize(1);
        String token = PasswordResetMails.extractToken(mailSender.sent().get(0));

        MvcTestResult confirm = confirm(token, NEW_PASSWORD);

        assertThat(confirm).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(login(member.getEmail(), NEW_PASSWORD)).hasStatus(HttpStatus.OK);
    }

    @Test
    @DisplayName("[F-02][PW-06] 재설정해도 그 전의 로그인 실패 기록은 지워지지 않는다")
    void resetKeepsFailureRecords() {
        Member member = saveMember();
        failLogin(member, 5);
        clock.advance(Duration.ofSeconds(1));

        assertThat(confirm(issueToken(member), NEW_PASSWORD)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(login(member.getEmail(), NEW_PASSWORD)).hasStatus(HttpStatus.OK);

        assertThat(count("login_history WHERE member_id = ? AND success = FALSE", member.getId()))
                .isEqualTo(5);
        assertThat(count("member WHERE id = ? AND password_changed_at IS NOT NULL", member.getId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("[F-02][PW-03][PW-06] 재설정한 뒤에는 실패를 처음부터 센다: 4번 실패까지는 로그인할 수 있고 5번째 실패 뒤에 잠긴다")
    void failuresAreCountedAgainAfterReset() {
        Member member = saveMember();
        failLogin(member, 5);
        clock.advance(Duration.ofSeconds(1));
        assertThat(confirm(issueToken(member), NEW_PASSWORD)).hasStatus(HttpStatus.NO_CONTENT);
        clock.advance(Duration.ofSeconds(1));

        failLogin(member, 4);
        MvcTestResult afterFourFailures = login(member.getEmail(), NEW_PASSWORD);
        failLogin(member, 5);
        MvcTestResult afterFiveFailures = login(member.getEmail(), NEW_PASSWORD);

        assertThat(afterFourFailures).hasStatus(HttpStatus.OK);
        assertThat(afterFiveFailures).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(afterFiveFailures).bodyJson().extractingPath("$.code").isEqualTo(LOGIN_LOCKED);
    }

    @Test
    @DisplayName("[F-02][PW-06] 한 회원이 재설정해도 다른 회원의 잠금은 그대로다")
    void resetDoesNotReleaseOtherMembers() {
        Member member = saveMember();
        Member other = saveMember();
        failLogin(member, 5);
        failLogin(other, 5);
        clock.advance(Duration.ofSeconds(1));

        assertThat(confirm(issueToken(member), NEW_PASSWORD)).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(login(member.getEmail(), NEW_PASSWORD)).hasStatus(HttpStatus.OK);
        MvcTestResult otherLocked = login(other.getEmail(), OLD_PASSWORD);
        assertThat(otherLocked).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(otherLocked).bodyJson().extractingPath("$.code").isEqualTo(LOGIN_LOCKED);
        assertThat(count("member WHERE id = ? AND password_changed_at IS NULL", other.getId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("[F-02][PW-06] 재설정이 실패하면(잘못된 토큰·만료·비밀번호 규칙 위반) 잠금은 풀리지 않고 변경 시각도 비어 있다")
    void failedResetKeepsLockout() {
        Member invalidMember = saveMember();
        Member expiredMember = saveMember();
        Member weakMember = saveMember();
        String expiredToken = issueToken(expiredMember);
        clock.advance(PasswordResetPolicy.TOKEN_VALIDITY);
        failLogin(invalidMember, 5);
        failLogin(expiredMember, 5);
        failLogin(weakMember, 5);
        String weakToken = issueToken(weakMember);

        MvcTestResult invalid = confirm(ResetToken.generate(new SecureRandom()).value(), NEW_PASSWORD);
        MvcTestResult expired = confirm(expiredToken, NEW_PASSWORD);
        MvcTestResult weak = confirm(weakToken, "Weak-1");

        assertThat(invalid).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(expired).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(weak).hasStatus(HttpStatus.BAD_REQUEST);
        for (Member member : List.of(invalidMember, expiredMember, weakMember)) {
            assertThat(login(member.getEmail(), OLD_PASSWORD)).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(count("member WHERE id = ? AND password_changed_at IS NULL", member.getId()))
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("[F-02][PW-06] 가입하지 않은 이메일의 실패 기록과 잠금은 다른 회원의 재설정에 영향받지 않는다")
    void unknownEmailLockoutIsUnaffected() {
        String unknownEmail = TestSequence.email();
        Member member = saveMember();
        for (int i = 0; i < 5; i++) {
            assertThat(login(unknownEmail, WRONG_PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);
        }
        clock.advance(Duration.ofSeconds(1));

        assertThat(confirm(issueToken(member), NEW_PASSWORD)).hasStatus(HttpStatus.NO_CONTENT);

        MvcTestResult locked = login(unknownEmail, OLD_PASSWORD);
        assertThat(locked).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(locked).bodyJson().extractingPath("$.code").isEqualTo(LOGIN_LOCKED);
        assertThat(count("login_history WHERE member_id IS NULL AND attempted_email = ?", unknownEmail))
                .isEqualTo(5);
    }

    @Test
    @DisplayName("[F-02][PW-03][PW-06] 재설정과 같은 시각의 실패는 재설정 전으로 세므로 잠금이 풀린다")
    void failuresAtTheSameInstantCountAsBeforeReset() {
        Member member = saveMember();
        failLogin(member, 5);

        MvcTestResult confirm = confirm(issueToken(member), NEW_PASSWORD);

        assertThat(confirm).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(login(member.getEmail(), NEW_PASSWORD)).hasStatus(HttpStatus.OK);
        assertThat(count("login_history WHERE member_id = ? AND success = FALSE", member.getId()))
                .isEqualTo(5);
    }

    @Test
    @DisplayName("[F-02][PW-06] 재설정한 뒤 1초가 지나서 쌓인 실패는 센다")
    void failureOneSecondAfterResetIsCounted() {
        Member member = saveMember();
        assertThat(confirm(issueToken(member), NEW_PASSWORD)).hasStatus(HttpStatus.NO_CONTENT);
        clock.advance(Duration.ofSeconds(1));

        failLogin(member, 5);

        assertThat(login(member.getEmail(), NEW_PASSWORD)).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
    }

    private Member saveMember() {
        return memberRepository.save(aMember()
                .email(TestSequence.email())
                .passwordHash(passwordEncoder.encode(OLD_PASSWORD))
                .now(clock.instant())
                .build());
    }

    // 토큰 원값은 DB에 없다. 메일을 거치지 않는 테스트는 토큰을 직접 만들어 해시만 저장한다.
    private String issueToken(Member member) {
        ResetToken token = ResetToken.generate(new SecureRandom());
        tokenRepository.save(PasswordResetToken.issue(member.getId(), ResetToken.hash(token.value()), clock.instant()));
        return token.value();
    }

    private void failLogin(Member member, int times) {
        for (int i = 0; i < times; i++) {
            assertThat(login(member.getEmail(), WRONG_PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);
        }
    }

    private MvcTestResult login(String email, String password) {
        return postJson(LOGIN_PATH, "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
    }

    private MvcTestResult confirm(String token, String newPassword) {
        return postJson(CONFIRM_PATH, "{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(token, newPassword));
    }

    private MvcTestResult postJson(String path, String body) {
        return mvc.post()
                .uri(path)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private int count(String fromAndWhere, Object... args) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + fromAndWhere, Integer.class, args);
        return count == null ? 0 : count;
    }
}
