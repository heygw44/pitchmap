package com.pitchmap.basecamp.application;

import com.pitchmap.basecamp.domain.Basecamp;
import com.pitchmap.basecamp.domain.BasecampRepository;
import com.pitchmap.basecamp.domain.CancelReason;
import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.outbox.OutboxEventRecorder;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 캠프 리더가 베이스캠프를 직접 마감, 재개, 확정, 취소한다. 확정과 취소는 멤버에게 알릴 이벤트를 같은 트랜잭션에서 기록한다.
 *
 * <p>합류 승인·탈퇴·강퇴와 겹쳐도 정원과 상태가 어긋나지 않도록, 베이스캠프 행을 쓰기 잠금으로 읽어 같은 베이스캠프의 쓰기를 한 번에 하나씩 처리한다.
 * 모든 동작은 다음 순서로 검사하고 처음 걸린 이유로 거부한다.
 * <ol>
 *   <li>베이스캠프가 없으면 NOT_FOUND
 *   <li>요청자가 캠프 리더가 아니면 ACCESS_DENIED
 *   <li>현재 상태에서 할 수 없는 동작이면 BASECAMP_INVALID_STATE
 *   <li>동작마다 따로 있는 이유(재개는 BASECAMP_FULL, 확정은 BASECAMP_NOT_ENOUGH_MEMBERS)
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class BasecampTransitionService {

    private final BasecampRepository basecampRepository;
    private final OutboxEventRecorder outboxEventRecorder;
    private final Clock clock;

    /** 호출하면 leaderId인 캠프 리더가 모집 중인 베이스캠프를 마감하고 그 ID와 상태를 돌려준다. */
    @Transactional
    public BasecampStatusResult close(long basecampId, long leaderId) {
        Basecamp basecamp = lockLedBy(basecampId, leaderId);
        basecamp.close(clock.instant());
        return resultOf(basecamp);
    }

    /** 호출하면 leaderId인 캠프 리더가 마감된 베이스캠프의 모집을 다시 열고, 정원이 가득 차 있으면 BASECAMP_FULL로 거부한다. */
    @Transactional
    public BasecampStatusResult reopen(long basecampId, long leaderId) {
        Basecamp basecamp = lockLedBy(basecampId, leaderId);
        basecamp.reopen(clock.instant());
        return resultOf(basecamp);
    }

    /**
     * 호출하면 leaderId인 캠프 리더가 베이스캠프를 확정하고, 캠프 리더를 포함한 ACTIVE 멤버 전원에게 알릴 이벤트를 기록한다.
     * 인원이 2명 미만이면 BASECAMP_NOT_ENOUGH_MEMBERS로 거부하고 이벤트도 기록하지 않는다.
     */
    @Transactional
    public BasecampStatusResult confirm(long basecampId, long leaderId) {
        Basecamp basecamp = lockLedBy(basecampId, leaderId);
        basecamp.confirm(clock.instant());
        recordTransition(BasecampTransitionEvents.CONFIRMED_EVENT_TYPE, basecamp, basecamp.activeMemberIds());
        return resultOf(basecamp);
    }

    /** 호출하면 leaderId인 캠프 리더가 베이스캠프를 취소하고, 캠프 리더를 뺀 ACTIVE 멤버에게 알릴 이벤트를 기록한다. */
    @Transactional
    public BasecampStatusResult cancel(long basecampId, long leaderId) {
        Basecamp basecamp = lockLedBy(basecampId, leaderId);
        basecamp.cancel(CancelReason.LEADER, clock.instant());
        List<Long> memberIds = basecamp.activeMemberIds().stream()
                .filter(memberId -> !basecamp.isLeader(memberId))
                .toList();
        recordTransition(BasecampTransitionEvents.CANCELED_EVENT_TYPE, basecamp, memberIds);
        return resultOf(basecamp);
    }

    // 베이스캠프 행의 잠금이 이 트랜잭션의 첫 조회여야 한다. MySQL의 반복 가능한 읽기에서는 잠그지 않는 조회가 먼저 일어나면
    // 그 시점에 읽기 스냅숏이 정해져서, 잠금을 얻은 뒤에도 먼저 끝난 다른 트랜잭션이 커밋한 변경이 보이지 않는다.
    private Basecamp lockLedBy(long basecampId, long leaderId) {
        Basecamp basecamp = basecampRepository
                .findByIdForUpdate(basecampId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!basecamp.isLeader(leaderId)) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
        return basecamp;
    }

    private void recordTransition(String eventType, Basecamp basecamp, List<Long> memberIds) {
        outboxEventRecorder.record(
                eventType,
                BasecampTransitionEvents.AGGREGATE_TYPE,
                basecamp.getId(),
                new BasecampTransitionEvents.Payload(basecamp.getId(), memberIds));
    }

    private static BasecampStatusResult resultOf(Basecamp basecamp) {
        return new BasecampStatusResult(basecamp.getId(), basecamp.getStatus().name());
    }
}
