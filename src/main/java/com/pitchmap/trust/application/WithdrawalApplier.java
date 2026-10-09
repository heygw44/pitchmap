package com.pitchmap.trust.application;

import com.pitchmap.common.outbox.OutboxEventRecorder;
import com.pitchmap.member.application.MemberWithdrawalService;
import com.pitchmap.trust.domain.IdentityVerification;
import com.pitchmap.trust.domain.IdentityVerificationRepository;
import com.pitchmap.trust.domain.SanctionRepository;
import com.pitchmap.trust.domain.SanctionStatus;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴에서 트랜잭션 하나로 묶는 부분이다. 회원 익명화, 본인확인 정보 파기, 후속 처리 이벤트 기록을 함께 커밋하거나 함께 되돌린다.
 * 세션 삭제는 {@link WithdrawalService}가 이 트랜잭션이 끝난 뒤에 한다.
 */
@Service
@RequiredArgsConstructor
public class WithdrawalApplier {

    private final MemberWithdrawalService memberWithdrawalService;
    private final IdentityVerificationRepository identityVerificationRepository;
    private final SanctionRepository sanctionRepository;
    private final OutboxEventRecorder outboxEventRecorder;
    private final Clock clock;

    /**
     * 호출하면 회원을 탈퇴 처리하고, 본인확인 기록이 있으면 출생연도·성별을 지운 뒤 CI 해시 보관 기한을 적고,
     * 진행 중인 베이스캠프와 행사 신청을 정리하라는 이벤트를 기록한다.
     *
     * <p>회원 행을 쓰기 잠금으로 읽는 일이 가장 먼저다. 제재 확정도 회원 행을 가장 먼저 잠그므로, 같은 회원에게 제재와 탈퇴가 겹쳐도
     * 잠그는 순서가 같아 서로 기다리다 멈추지 않는다. 이미 탈퇴한 회원이면 {@code LOGIN_FAILED}로 거부한다.
     */
    @Transactional
    public void apply(long memberId) {
        memberWithdrawalService.withdraw(memberId);
        Instant now = clock.instant();
        identityVerificationRepository.findByMemberId(memberId).ifPresent(identity -> {
            boolean hasSanctionHistory =
                    sanctionRepository.existsByMemberIdAndStatusNot(memberId, SanctionStatus.LIFTED);
            identity.withdraw(IdentityVerification.ciRetainedUntil(now, hasSanctionHistory), now);
        });
        recordCleanup(WithdrawalEvents.BASECAMP_CLEANUP_EVENT_TYPE, memberId);
        recordCleanup(WithdrawalEvents.PROGRAM_CLEANUP_EVENT_TYPE, memberId);
    }

    private void recordCleanup(String eventType, long memberId) {
        outboxEventRecorder.record(
                eventType, WithdrawalEvents.AGGREGATE_TYPE, memberId, new WithdrawalEvents.Payload(memberId));
    }
}
