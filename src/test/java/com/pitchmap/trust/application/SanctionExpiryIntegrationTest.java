package com.pitchmap.trust.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.application.LoginCommand;
import com.pitchmap.member.application.MemberLoginService;
import com.pitchmap.member.domain.MemberErrorCode;
import com.pitchmap.member.domain.MemberStatus;
import com.pitchmap.member.domain.MemberSuspendedException;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.trust.domain.SanctionType;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@IntegrationTest
class SanctionExpiryIntegrationTest {

    private static final String PASSWORD = "Valid-pass1";
    private static final String IP = "203.0.113.7";

    @Autowired
    private SanctionExpiryService sanctionExpiryService;

    @Autowired
    private SanctionConfirmService sanctionConfirmService;

    @Autowired
    private MemberLoginService memberLoginService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    private CompanionReviewFixture fixture;
    private long adminId;

    @BeforeEach
    void setUp() {
        fixture = new CompanionReviewFixture(jdbc, memberRepository);
        adminId = fixture.saveVerifiedMember(TestSequence.nickname());
    }

    @Test
    @DisplayName("[SN-10] 7일이 지나면 정지 제재는 EXPIRED, 회원은 ACTIVE가 되고, 영구 정지와 경고 제재는 그대로다")
    void expiresEndedSuspensionAndReleasesMember() {
        long temporary = emailVerifiedMember();
        long permanent = emailVerifiedMember();
        long warned = emailVerifiedMember();
        confirm(temporary, SanctionType.WARNING);
        confirm(temporary, SanctionType.SUSPEND_7D);
        confirm(permanent, SanctionType.PERMANENT);
        confirm(warned, SanctionType.WARNING);

        clock.advance(Duration.ofDays(7).minusSeconds(1));
        SanctionExpiryService.Result beforeEnd = sanctionExpiryService.run();
        clock.advance(Duration.ofSeconds(1));
        SanctionExpiryService.Result atEnd = sanctionExpiryService.run();
        SanctionExpiryService.Result again = sanctionExpiryService.run();

        assertThat(beforeEnd).isEqualTo(new SanctionExpiryService.Result(0, 0));
        assertThat(atEnd).isEqualTo(new SanctionExpiryService.Result(1, 1));
        assertThat(again).isEqualTo(new SanctionExpiryService.Result(0, 0));
        assertThat(sanctionStatus(temporary, "SUSPEND_7D")).isEqualTo("EXPIRED");
        assertThat(sanctionStatus(permanent, "PERMANENT")).isEqualTo("ACTIVE");
        assertThat(sanctionStatus(warned, "WARNING")).isEqualTo("ACTIVE");
        assertThat(memberStatus(temporary)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT suspended_until FROM member WHERE id = ?", Object.class, temporary))
                .isNull();
        assertThat(memberStatus(permanent)).isEqualTo("SUSPENDED");
        assertThat(memberStatus(warned)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("[SN-10] 이메일 인증을 마치지 못한 회원은 정지가 풀려도 UNVERIFIED로 돌아간다")
    void unverifiedMemberReturnsToUnverified() {
        long member = fixture.saveVerifiedMember(TestSequence.nickname());
        confirm(member, SanctionType.WARNING);
        confirm(member, SanctionType.SUSPEND_7D);

        clock.advance(Duration.ofDays(7));
        sanctionExpiryService.run();

        assertThat(memberStatus(member)).isEqualTo("UNVERIFIED");
    }

    @Test
    @DisplayName("[SN-10] 정지 기간이 끝난 회원은 만료 작업이 돌기 전에도 로그인에 성공하고, 기간 중에는 MEMBER_SUSPENDED다")
    void loginReleasesEndedSuspension() {
        long member = emailVerifiedMember();
        confirm(member, SanctionType.WARNING);
        confirm(member, SanctionType.SUSPEND_7D);
        String email = jdbc.queryForObject("SELECT email FROM member WHERE id = ?", String.class, member);
        jdbc.update("UPDATE member SET password_hash = ? WHERE id = ?", passwordEncoder.encode(PASSWORD), member);
        LoginCommand login = new LoginCommand(email, PASSWORD, IP);

        clock.advance(Duration.ofDays(7).minusSeconds(1));
        assertThatThrownBy(() -> memberLoginService.login(login))
                .isInstanceOfSatisfying(
                        MemberSuspendedException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_SUSPENDED));
        clock.advance(Duration.ofSeconds(1));
        var result = memberLoginService.login(login);

        assertThat(result.status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(memberStatus(member)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("[SN-10] 영구 정지 회원은 시간이 지나도 로그인할 수 없다")
    void permanentSuspensionNeverEnds() {
        long member = emailVerifiedMember();
        confirm(member, SanctionType.PERMANENT);
        String email = jdbc.queryForObject("SELECT email FROM member WHERE id = ?", String.class, member);
        jdbc.update("UPDATE member SET password_hash = ? WHERE id = ?", passwordEncoder.encode(PASSWORD), member);

        clock.advance(Duration.ofDays(3650));

        assertThatThrownBy(() -> memberLoginService.login(new LoginCommand(email, PASSWORD, IP)))
                .isInstanceOf(MemberSuspendedException.class);
    }

    private long emailVerifiedMember() {
        long member = fixture.saveVerifiedMember(TestSequence.nickname());
        jdbc.update("UPDATE member SET status = 'ACTIVE', email_verified_at = NOW(6) WHERE id = ?", member);
        return member;
    }

    private void confirm(long memberId, SanctionType type) {
        sanctionConfirmService.confirm(new SanctionConfirmCommand(memberId, null, type, "반복 위반", adminId));
    }

    private String sanctionStatus(long memberId, String type) {
        return jdbc.queryForObject(
                "SELECT status FROM sanction WHERE member_id = ? AND type = ?", String.class, memberId, type);
    }

    private String memberStatus(long memberId) {
        return jdbc.queryForObject("SELECT status FROM member WHERE id = ?", String.class, memberId);
    }
}
