package com.pitchmap.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.domain.DisposableEmailDomain;
import com.pitchmap.member.domain.DisposableEmailDomainSource;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.domain.MemberErrorCode;
import com.pitchmap.member.domain.MemberException;
import com.pitchmap.member.domain.MemberRole;
import com.pitchmap.member.domain.MemberStatus;
import com.pitchmap.member.infra.DisposableEmailDomainJpaRepository;
import com.pitchmap.member.infra.MemberJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

@IntegrationTest
class MemberSignupServiceIntegrationTest {

    private static final String VALID_PASSWORD = "Passw0rd!xyz";
    private static final String REQUEST_IP = "203.0.113.7";

    @Autowired
    MemberSignupService memberSignupService;

    @Autowired
    MemberJpaRepository memberJpaRepository;

    @Autowired
    DisposableEmailDomainJpaRepository disposableEmailDomainJpaRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("[F-01] 유효한 입력이면 UNVERIFIED USER 회원이 시계 시각으로 저장된다")
    void signUpStoresUnverifiedUser() {
        // given
        String email = TestSequence.email();

        // when
        SignupResult result =
                memberSignupService.signUp(new SignupCommand(email, VALID_PASSWORD, nickname(), REQUEST_IP));

        // then
        assertThat(result.memberId()).isNotNull();
        assertThat(result.status()).isEqualTo(MemberStatus.UNVERIFIED);
        Member member = memberJpaRepository.findById(result.memberId()).orElseThrow();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.UNVERIFIED);
        assertThat(member.getRole()).isEqualTo(MemberRole.USER);
        assertThat(member.getEmail()).isEqualTo(email);
        assertThat(member.getCreatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
    }

    @Test
    @DisplayName("[F-01][PW-01] 비밀번호는 BCrypt 해시로 저장된다")
    void passwordIsStoredAsBcryptHash() {
        // when
        SignupResult result = memberSignupService.signUp(
                new SignupCommand(TestSequence.email(), VALID_PASSWORD, nickname(), REQUEST_IP));

        // then
        String hash =
                memberJpaRepository.findById(result.memberId()).orElseThrow().getPasswordHash();
        assertThat(hash).isNotEqualTo(VALID_PASSWORD);
        assertThat(hash).startsWith("$2");
        assertThat(hash.length()).isLessThanOrEqualTo(100);
        assertThat(passwordEncoder.matches(VALID_PASSWORD, hash)).isTrue();
    }

    @Test
    @DisplayName("[F-01][EV-06] 이메일은 소문자로 저장되고 대소문자만 다른 이메일은 중복이다")
    void emailIsLowercasedAndDuplicateIgnoresCase() {
        // given
        SignupResult first = memberSignupService.signUp(
                new SignupCommand("User@Example.com", VALID_PASSWORD, nickname(), REQUEST_IP));

        // when & then
        assertThat(memberJpaRepository.findById(first.memberId()).orElseThrow().getEmail())
                .isEqualTo("user@example.com");
        SignupCommand duplicate = new SignupCommand("USER@EXAMPLE.COM", VALID_PASSWORD, nickname(), REQUEST_IP);
        assertThatThrownBy(() -> memberSignupService.signUp(duplicate))
                .isInstanceOfSatisfying(
                        MemberException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_EMAIL_DUPLICATED));
        assertThat(memberJpaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-01] 닉네임이 중복이면 MEMBER_NICKNAME_DUPLICATED")
    void duplicateNicknameIsRejected() {
        // given
        String nickname = nickname();
        memberSignupService.signUp(new SignupCommand(TestSequence.email(), VALID_PASSWORD, nickname, REQUEST_IP));

        // when & then
        SignupCommand duplicate = new SignupCommand(TestSequence.email(), VALID_PASSWORD, nickname, REQUEST_IP);
        assertThatThrownBy(() -> memberSignupService.signUp(duplicate))
                .isInstanceOfSatisfying(
                        MemberException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NICKNAME_DUPLICATED));
        assertThat(memberJpaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-01][EV-04] 차단 목록 도메인과 그 하위 도메인 이메일은 거부되고, 목록에 없는 도메인은 통과한다")
    void disposableEmailDomainIsRejected() {
        // given
        disposableEmailDomainJpaRepository.save(DisposableEmailDomain.of(
                "mailinator.com", DisposableEmailDomainSource.PUBLIC, MutableClock.DEFAULT_INSTANT));

        // when & then
        assertDisposableRejected("x@mailinator.com");
        assertDisposableRejected("x@a.mailinator.com");
        assertThat(memberJpaRepository.count()).isZero();
        SignupResult result = memberSignupService.signUp(
                new SignupCommand("x@notmailinator.com", VALID_PASSWORD, nickname(), REQUEST_IP));
        assertThat(result.memberId()).isNotNull();
    }

    @Test
    @DisplayName("[F-01][PW-01] 비밀번호 정책을 어기면 MEMBER_PASSWORD_POLICY이고 회원이 만들어지지 않는다")
    void passwordPolicyViolationIsRejected() {
        // given
        SignupCommand command = new SignupCommand(TestSequence.email(), "Passw0r!x", nickname(), REQUEST_IP);

        // when & then
        assertThatThrownBy(() -> memberSignupService.signUp(command))
                .isInstanceOfSatisfying(
                        MemberException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_PASSWORD_POLICY));
        assertThat(memberJpaRepository.count()).isZero();
    }

    private void assertDisposableRejected(String email) {
        SignupCommand command = new SignupCommand(email, VALID_PASSWORD, nickname(), REQUEST_IP);
        assertThatThrownBy(() -> memberSignupService.signUp(command))
                .isInstanceOfSatisfying(
                        MemberException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_DISPOSABLE_EMAIL));
    }

    private String nickname() {
        return TestSequence.nickname();
    }
}
