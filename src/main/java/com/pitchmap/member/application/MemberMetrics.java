package com.pitchmap.member.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 가입 수와 이메일 인증 완료 수를 센다. 운영자는 두 값의 비율로 가입한 회원 중 인증까지 마친 비율을 본다.
 *
 * <p>가입과 인증은 트랜잭션 안에서 성공이 정해진다. 그래서 트랜잭션이 커밋된 뒤에만 수를 올린다. 커밋 전에 올리면 롤백된 가입까지 세어진다.
 */
@Component
public class MemberMetrics {

    private final Counter signups;
    private final Counter emailVerifications;

    public MemberMetrics(MeterRegistry meterRegistry) {
        this.signups = Counter.builder("pitchmap.member.signups")
                .description("가입을 마친 회원 수")
                .register(meterRegistry);
        this.emailVerifications = Counter.builder("pitchmap.member.email.verifications")
                .description("이메일 인증을 마친 회원 수")
                .register(meterRegistry);
    }

    /** 호출하면 지금 트랜잭션이 커밋된 뒤 가입 수를 1 올린다. 트랜잭션 밖에서 부르면 바로 올린다. */
    public void recordSignup() {
        afterCommit(signups);
    }

    /** 호출하면 지금 트랜잭션이 커밋된 뒤 이메일 인증 완료 수를 1 올린다. 트랜잭션 밖에서 부르면 바로 올린다. */
    public void recordEmailVerified() {
        afterCommit(emailVerifications);
    }

    private static void afterCommit(Counter counter) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            counter.increment();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                counter.increment();
            }
        });
    }
}
