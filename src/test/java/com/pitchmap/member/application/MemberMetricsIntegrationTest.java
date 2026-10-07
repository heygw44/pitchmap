package com.pitchmap.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.domain.MemberException;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

// 통합 테스트는 Spring 컨텍스트와 지표 저장소를 공유한다. 그래서 각 테스트는 지표의 절대값이 아니라 호출 전후의 차이를 본다.
@IntegrationTest
class MemberMetricsIntegrationTest {

    private static final String VALID_PASSWORD = "Passw0rd!xyz";
    private static final String REQUEST_IP = "203.0.113.7";

    @Autowired
    private MemberSignupService memberSignupService;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("[NFR-09] 가입이 커밋되면 가입 수가 1 오른다")
    void countsCommittedSignup() {
        double before = signups();

        signUp(TestSequence.email());

        assertThat(signups()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("[NFR-09] 이미 쓰는 이메일로 가입에 실패하면 가입 수가 오르지 않는다")
    void doesNotCountRejectedSignup() {
        String email = TestSequence.email();
        signUp(email);
        double before = signups();

        assertThatThrownBy(() -> signUp(email)).isInstanceOf(MemberException.class);

        assertThat(signups()).isEqualTo(before);
    }

    @Test
    @DisplayName("[NFR-09] 가입을 감싼 트랜잭션이 롤백되면 가입 수가 오르지 않는다")
    void doesNotCountRolledBackSignup() {
        double before = signups();

        transactionTemplate.executeWithoutResult(status -> {
            signUp(TestSequence.email());
            status.setRollbackOnly();
        });

        assertThat(signups()).isEqualTo(before);
    }

    @Test
    @DisplayName("[NFR-09] 맞는 코드로 이메일 인증을 마치면 인증 완료 수가 1 오르고, 틀린 코드는 세지 않는다")
    void countsOnlySuccessfulEmailVerification() {
        // given
        long memberId = signUp(TestSequence.email()).memberId();
        String code = emailVerificationService
                .issueFor(memberId, REQUEST_IP)
                .orElseThrow()
                .code();
        String wrongCode = code.equals("000000") ? "111111" : "000000";
        double before = emailVerifications();

        // when
        assertThatThrownBy(() -> emailVerificationService.verify(memberId, wrongCode))
                .isInstanceOf(MemberException.class);
        double afterWrongCode = emailVerifications();
        emailVerificationService.verify(memberId, code);

        // then
        assertThat(afterWrongCode).isEqualTo(before);
        assertThat(emailVerifications()).isEqualTo(before + 1);
    }

    private SignupResult signUp(String email) {
        return memberSignupService.signUp(
                new SignupCommand(email, VALID_PASSWORD, TestSequence.nickname(), REQUEST_IP));
    }

    private double signups() {
        return meterRegistry.get("pitchmap.member.signups").counter().count();
    }

    private double emailVerifications() {
        return meterRegistry
                .get("pitchmap.member.email.verifications")
                .counter()
                .count();
    }
}
