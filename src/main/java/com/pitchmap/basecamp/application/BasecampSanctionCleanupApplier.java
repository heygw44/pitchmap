package com.pitchmap.basecamp.application;

import com.pitchmap.basecamp.domain.Basecamp;
import com.pitchmap.basecamp.domain.BasecampRepository;
import com.pitchmap.basecamp.domain.SanctionRemoval;
import com.pitchmap.common.outbox.OutboxEventRecorder;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제재를 받은 회원을 베이스캠프 하나에서 정리한다. 메서드 하나가 트랜잭션 하나이고 베이스캠프 하나만 다룬다.
 * 그래서 한 베이스캠프의 처리가 실패해도 다른 베이스캠프의 처리는 그대로 커밋된다.
 */
@Component
@RequiredArgsConstructor
public class BasecampSanctionCleanupApplier {

    private final BasecampRepository basecampRepository;
    private final OutboxEventRecorder outboxEventRecorder;
    private final Clock clock;

    /**
     * 호출하면 memberId인 회원을 basecampId인 베이스캠프에서 정리하고, 남은 멤버에게 알릴 이벤트를 같은 트랜잭션에서 기록한다.
     * 캠프 리더이면 베이스캠프를 취소하고 캠프 리더를 뺀 ACTIVE 멤버에게 알린다. 멤버이면 탈퇴 처리하고 남은 ACTIVE 멤버에게 알린다.
     * 대기 중인 신청자이면 신청만 취소하고 알리지 않는다. 이미 빠졌거나 끝난 베이스캠프이면 아무것도 바꾸지 않고 이벤트도 기록하지 않는다.
     */
    @Transactional
    public SanctionRemoval cleanUp(long basecampId, long memberId) {
        // 베이스캠프 행을 쓰기 잠금으로 읽는 조회가 이 트랜잭션의 첫 조회여야 한다. MySQL의 반복 가능한 읽기에서는
        // 잠그지 않는 조회가 먼저 일어나면 그 시점에 읽기 스냅숏이 정해져서, 잠금을 얻은 뒤에도 먼저 끝난 다른 트랜잭션의
        // 커밋이 보이지 않는다. 그러면 이미 빠진 멤버를 다시 처리하거나 남은 멤버를 잘못 알린다.
        Basecamp basecamp = basecampRepository.findByIdForUpdate(basecampId).orElse(null);
        if (basecamp == null) {
            return SanctionRemoval.NOTHING;
        }
        SanctionRemoval removal = basecamp.removeSanctionedMember(memberId, clock.instant());
        switch (removal) {
            case LEADER_CANCELED -> recordCanceled(basecamp);
            case MEMBER_LEFT -> recordMemberChanged(basecamp);
            case APPLICATION_CANCELED, NOTHING -> {}
        }
        return removal;
    }

    private void recordCanceled(Basecamp basecamp) {
        List<Long> memberIds = basecamp.activeMemberIds().stream()
                .filter(memberId -> !basecamp.isLeader(memberId))
                .toList();
        outboxEventRecorder.record(
                BasecampTransitionEvents.CANCELED_EVENT_TYPE,
                BasecampTransitionEvents.AGGREGATE_TYPE,
                basecamp.getId(),
                new BasecampTransitionEvents.Payload(basecamp.getId(), memberIds));
    }

    private void recordMemberChanged(Basecamp basecamp) {
        outboxEventRecorder.record(
                BasecampMembershipEvents.MEMBER_CHANGED_EVENT_TYPE,
                BasecampMembershipEvents.AGGREGATE_TYPE,
                basecamp.getId(),
                new BasecampMembershipEvents.MemberChangedPayload(basecamp.getId(), basecamp.activeMemberIds()));
    }
}
