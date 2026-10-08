package com.pitchmap.trust.application;

import com.pitchmap.common.outbox.OutboxEventRecorder;
import com.pitchmap.member.application.MemberSuspensionService;
import com.pitchmap.trust.domain.Sanction;
import com.pitchmap.trust.domain.SanctionPolicy;
import com.pitchmap.trust.domain.SanctionRepository;
import com.pitchmap.trust.domain.SanctionType;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제재 확정에서 트랜잭션 하나로 묶는 부분이다. 제재 기록, 회원 정지, 후속 처리 이벤트 기록을 함께 커밋하거나 함께 되돌린다.
 * 세션 삭제는 {@link SanctionConfirmService}가 이 트랜잭션이 끝난 뒤에 한다.
 */
@Service
@RequiredArgsConstructor
public class SanctionConfirmApplier {

    private final SanctionRepository sanctionRepository;
    private final MemberSuspensionService memberSuspensionService;
    private final OutboxEventRecorder outboxEventRecorder;
    private final Clock clock;

    /**
     * 호출하면 대상 회원에게 제재를 확정해 저장한다. 제재가 정지이면 같은 트랜잭션에서 회원을 정지하고, 진행 중인 베이스캠프를 정리하라는
     * 이벤트를 기록한다. 경고는 회원 상태를 바꾸지 않고 정리 이벤트도 기록하지 않는다. 제재받은 회원에게 알리는 이벤트는 경고를 포함한 모든 제재에 기록한다.
     *
     * <p>회원 행을 쓰기 잠금으로 읽는 일이 가장 먼저다. 제재 행을 넣으면 외래 키 때문에 회원 행에 읽기 잠금이 먼저 걸리는데,
     * 같은 회원에게 동시에 제재를 확정하면 둘 다 읽기 잠금을 쥔 채 쓰기 잠금을 기다리다 데드락이 난다.
     * 이 잠금 덕분에 같은 회원의 제재가 한 줄로 처리되어, 같은 단계가 두 번 계산되지 않는다.
     *
     * <p>요청한 종류가 이 회원의 다음 제재 단계와 다르면(영구 정지를 곧바로 지정하는 경우는 제외) INVALID_INPUT으로 거부한다.
     * 대상 회원이 없는지는 호출하는 쪽이 먼저 걸러야 한다.
     */
    @Transactional
    public SanctionConfirmResult apply(SanctionConfirmCommand command) {
        long memberId = command.targetMemberId();
        memberSuspensionService.lockForSanction(memberId);
        Instant now = clock.instant();
        Integer highestLevel = sanctionRepository
                .findHighestConfirmedLevel(memberId)
                .map(Byte::intValue)
                .orElse(null);
        int level = SanctionPolicy.decideLevel(highestLevel, command.type());
        Sanction sanction = sanctionRepository.save(Sanction.confirm(
                memberId, command.reportId(), command.type(), level, command.reason(), now, command.adminId()));
        boolean suspended = command.type().isSuspension();
        if (suspended) {
            suspend(sanction);
            outboxEventRecorder.record(
                    SanctionEvents.BASECAMP_CLEANUP_EVENT_TYPE,
                    SanctionEvents.AGGREGATE_TYPE,
                    memberId,
                    new SanctionEvents.Payload(memberId, sanction.getId()));
        }
        outboxEventRecorder.record(
                SanctionEvents.NOTIFICATION_EVENT_TYPE,
                SanctionEvents.AGGREGATE_TYPE,
                memberId,
                new SanctionEvents.NotificationPayload(
                        memberId, sanction.getId(), sanction.getType().name(), sanction.getEndsAt()));
        return new SanctionConfirmResult(sanction.getId(), memberId, suspended);
    }

    // 7일·30일 정지는 이미 더 늦게 끝나는 정지가 있으면 그것을 유지한다. 영구 정지는 종료 시각이 없다.
    private void suspend(Sanction sanction) {
        if (sanction.getType() == SanctionType.PERMANENT) {
            memberSuspensionService.suspendPermanently(sanction.getMemberId());
            return;
        }
        memberSuspensionService.suspendTemporarily(sanction.getMemberId(), sanction.getEndsAt());
    }
}
