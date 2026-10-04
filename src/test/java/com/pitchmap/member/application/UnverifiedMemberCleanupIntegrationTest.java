package com.pitchmap.member.application;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.domain.MemberStatus;
import com.pitchmap.member.domain.UnverifiedMemberPolicy;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.member.infra.UnverifiedMemberMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
class UnverifiedMemberCleanupIntegrationTest {

    private static final String VALID_PASSWORD = "Valid-pass1";
    private static final Duration ONE_SECOND = Duration.ofSeconds(1);
    // DATETIME 컬럼에 UTC 시각을 문자열로 넣는다. 드라이버의 시간대 변환이 끼어들지 않게 하려는 것이다.
    private static final DateTimeFormatter DATETIME_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC);

    @Autowired
    private UnverifiedMemberCleanupService cleanupService;

    @Autowired
    private UnverifiedMemberMapper unverifiedMemberMapper;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    private String passwordHash;

    @BeforeEach
    void encodePassword() {
        passwordHash = passwordEncoder.encode(VALID_PASSWORD);
    }

    @Test
    @DisplayName("[F-01][EV-05] 가입한 지 7일이 1초 지난 미인증 회원을 삭제하고 인증 코드 행도 함께 사라진다")
    void deletesUnverifiedMemberOlderThanSevenDaysWithVerificationRows() {
        Member expired = saveMember(sevenDaysAgo().minus(ONE_SECOND));
        insertVerification(expired.getId());
        insertVerification(expired.getId());

        int deleted = cleanupService.deleteExpiredUnverifiedMembers();

        assertThat(deleted).isEqualTo(1);
        assertThat(count("member WHERE id = ?", expired.getId())).isZero();
        assertThat(count("email_verification WHERE member_id = ?", expired.getId()))
                .isZero();
    }

    @Test
    @DisplayName("[F-01][EV-05] 정확히 7일 된 미인증 회원은 남기고, 7일이 되기 1초 전인 회원도 남긴다")
    void keepsUnverifiedMembersNotOlderThanSevenDays() {
        Member exactlySevenDays = saveMember(sevenDaysAgo());
        Member justUnderSevenDays = saveMember(sevenDaysAgo().plus(ONE_SECOND));
        Member recent = saveMember(clock.instant());

        int deleted = cleanupService.deleteExpiredUnverifiedMembers();

        assertThat(deleted).isZero();
        assertThat(count(
                        "member WHERE id IN (?, ?, ?)",
                        exactlySevenDays.getId(),
                        justUnderSevenDays.getId(),
                        recent.getId()))
                .isEqualTo(3);
    }

    @Test
    @DisplayName("[F-01][EV-05] 시계가 움직여 7일을 넘기면 그때부터 삭제 대상이 된다")
    void memberBecomesDeletableWhenClockPassesSevenDays() {
        Member member = saveMember(clock.instant());

        assertThat(cleanupService.deleteExpiredUnverifiedMembers()).isZero();
        clock.advance(UnverifiedMemberPolicy.UNVERIFIED_RETENTION);
        assertThat(cleanupService.deleteExpiredUnverifiedMembers()).isZero();
        clock.advance(ONE_SECOND);
        int deleted = cleanupService.deleteExpiredUnverifiedMembers();

        assertThat(deleted).isEqualTo(1);
        assertThat(count("member WHERE id = ?", member.getId())).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
            value = MemberStatus.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = {"UNVERIFIED"})
    @DisplayName("[F-01][EV-05] 가입한 지 오래됐어도 미인증이 아닌 회원은 삭제하지 않는다")
    void keepsOldMembersThatAreNotUnverified(MemberStatus status) {
        Member old = saveMember(sevenDaysAgo().minus(Duration.ofDays(30)));
        jdbc.update("UPDATE member SET status = ? WHERE id = ?", status.name(), old.getId());

        int deleted = cleanupService.deleteExpiredUnverifiedMembers();

        assertThat(deleted).isZero();
        assertThat(count("member WHERE id = ? AND status = ?", old.getId(), status.name()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("[F-01][EV-05] 삭제하는 회원의 세션은 SPRING_SESSION에서 사라지고 남는 회원의 세션은 그대로다")
    void deletesSessionsOfDeletedMembersOnly() {
        String expiredEmail = TestSequence.email();
        String recentEmail = TestSequence.email();
        Member expired = saveMember(expiredEmail, sevenDaysAgo().minus(ONE_SECOND));
        Member recent = saveMember(recentEmail, clock.instant());
        login(expiredEmail);
        login(expiredEmail);
        login(recentEmail);
        assertThat(sessionCount(expired.getId())).isEqualTo(2);
        assertThat(sessionCount(recent.getId())).isEqualTo(1);

        cleanupService.deleteExpiredUnverifiedMembers();

        assertThat(sessionCount(expired.getId())).isZero();
        assertThat(sessionCount(recent.getId())).isEqualTo(1);
        assertThat(count("SPRING_SESSION")).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-01][EV-05] 삭제한 회원의 접속 기록은 남고 member_id만 NULL이 된다")
    void keepsLoginHistoryWithNullMemberId() {
        String email = TestSequence.email();
        Member expired = saveMember(email, sevenDaysAgo().minus(ONE_SECOND));
        login(email);
        assertThat(count("login_history WHERE member_id = ?", expired.getId())).isEqualTo(1);

        cleanupService.deleteExpiredUnverifiedMembers();

        assertThat(count("login_history WHERE member_id = ?", expired.getId())).isZero();
        assertThat(count("login_history WHERE attempted_email = ? AND member_id IS NULL", email))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("[F-01][EV-05] 삭제 대상을 조회한 뒤 인증을 마친 회원은 조건부 삭제가 건너뛰어 지워지지 않는다")
    void doesNotDeleteMemberVerifiedAfterSelection() {
        Member verifiedMeanwhile = saveMember(sevenDaysAgo().minus(ONE_SECOND));
        Member stillUnverified = saveMember(sevenDaysAgo().minus(ONE_SECOND));
        Instant cutoff = UnverifiedMemberPolicy.deletionCutoff(clock.instant());
        List<Long> selected =
                unverifiedMemberMapper.selectExpiredIds(cutoff, UnverifiedMemberCleanupService.BATCH_SIZE);
        assertThat(selected).containsExactly(verifiedMeanwhile.getId(), stillUnverified.getId());
        jdbc.update(
                "UPDATE member SET status = 'ACTIVE', email_verified_at = ? WHERE id = ?",
                DATETIME_UTC.format(clock.instant()),
                verifiedMeanwhile.getId());

        int deleted = unverifiedMemberMapper.deleteExpiredByIds(selected, cutoff);

        assertThat(deleted).isEqualTo(1);
        assertThat(count("member WHERE id = ? AND status = 'ACTIVE'", verifiedMeanwhile.getId()))
                .isEqualTo(1);
        assertThat(count("member WHERE id = ?", stillUnverified.getId())).isZero();
    }

    @Test
    @DisplayName("[F-01][EV-05] 한 번에 지우는 상한보다 대상이 많아도 모두 삭제하고 지운 수를 돌려준다")
    void deletesAllExpiredMembersBeyondBatchSize() {
        int total = UnverifiedMemberCleanupService.BATCH_SIZE * 2 + 1;
        insertExpiredMembers(total);
        Member activeOld = saveMember(sevenDaysAgo().minus(Duration.ofDays(30)));
        jdbc.update("UPDATE member SET status = 'ACTIVE' WHERE id = ?", activeOld.getId());
        Member recent = saveMember(clock.instant());

        int deleted = cleanupService.deleteExpiredUnverifiedMembers();

        assertThat(deleted).isEqualTo(total);
        assertThat(count("member")).isEqualTo(2);
        assertThat(count("member WHERE id IN (?, ?)", activeOld.getId(), recent.getId()))
                .isEqualTo(2);
    }

    private Instant sevenDaysAgo() {
        return UnverifiedMemberPolicy.deletionCutoff(clock.instant());
    }

    private Member saveMember(Instant createdAt) {
        return saveMember(TestSequence.email(), createdAt);
    }

    private Member saveMember(String email, Instant createdAt) {
        return memberRepository.save(
                aMember().email(email).passwordHash(passwordHash).now(createdAt).build());
    }

    private void insertVerification(long memberId) {
        String now = DATETIME_UTC.format(clock.instant());
        jdbc.update(
                "INSERT INTO email_verification"
                        + " (member_id, code_hash, request_ip, attempt_count, expires_at, created_at, updated_at)"
                        + " VALUES (?, ?, '127.0.0.1', 0, ?, ?, ?)",
                memberId,
                "a".repeat(64),
                DATETIME_UTC.format(clock.instant().plus(Duration.ofMinutes(10))),
                now,
                now);
    }

    private void insertExpiredMembers(int count) {
        String createdAt = DATETIME_UTC.format(sevenDaysAgo().minus(ONE_SECOND));
        List<Object[]> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new Object[] {TestSequence.email(), passwordHash, TestSequence.nickname(), createdAt, createdAt});
        }
        jdbc.batchUpdate(
                "INSERT INTO member (email, password_hash, nickname, status, role, created_at, updated_at)"
                        + " VALUES (?, ?, ?, 'UNVERIFIED', 'USER', ?, ?)",
                rows);
    }

    private void login(String email) {
        MvcTestResult result = mvc.post()
                .uri("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, VALID_PASSWORD))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
    }

    private int sessionCount(long memberId) {
        return count("SPRING_SESSION WHERE PRINCIPAL_NAME = ?", String.valueOf(memberId));
    }

    private int count(String fromAndWhere, Object... args) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + fromAndWhere, Integer.class, args);
        return count == null ? 0 : count;
    }
}
