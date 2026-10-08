package com.pitchmap.basecamp.application;

import com.pitchmap.basecamp.domain.Basecamp;
import com.pitchmap.basecamp.domain.BasecampStatus;
import com.pitchmap.basecamp.infra.BasecampAutoTransitionMapper;
import com.pitchmap.common.outbox.OutboxEventRecorder;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 베이스캠프 하나를 자동으로 확정, 취소, 완료한다. 메서드 하나가 트랜잭션 하나이고 베이스캠프 하나만 다룬다.
 * 그래서 한 베이스캠프의 처리가 실패해도 다른 베이스캠프의 처리는 그대로 커밋된다.
 *
 * <p>캠프 리더의 직접 전이나 멤버 탈퇴와 겹쳐도 한 번만 처리하도록, 엔티티를 읽지 않고 상태 조건을 건 UPDATE로 바꾼다.
 * 영향받은 행이 0이면 다른 쪽이 먼저 처리한 것이므로 아무것도 기록하지 않고 false를 돌려준다.
 */
@Component
@RequiredArgsConstructor
public class BasecampAutoTransitionApplier {

    private final BasecampAutoTransitionMapper autoTransitionMapper;
    private final OutboxEventRecorder outboxEventRecorder;

    /**
     * 호출하면 모집 중이거나 마감된 베이스캠프를 인원이 {@value Basecamp#MIN_CONFIRM_HEADCOUNT}명 이상이면 확정하고, 아니면 취소한다.
     * 결정되지 않은 신청은 만료시키고, 캠프 리더를 포함한 ACTIVE 멤버 전원에게 알릴 이벤트를 같은 트랜잭션에서 기록한다.
     * 다른 쪽이 이미 확정하거나 취소했으면 아무것도 바꾸지 않고 false를 돌려준다.
     */
    @Transactional
    public boolean processDayBeforeDeparture(long basecampId, Instant now) {
        // 베이스캠프 행을 쓰기 잠금으로 읽는 조회가 이 트랜잭션의 첫 조회여야 한다. MySQL의 반복 가능한 읽기에서는
        // 잠그지 않는 조회가 먼저 일어나면 그 시점에 읽기 스냅숏이 정해져서, 잠금을 얻은 뒤에도 먼저 끝난 탈퇴 같은
        // 다른 트랜잭션의 커밋이 보이지 않는다. 그러면 이미 빠진 멤버를 세어 인원을 잘못 판단한다.
        String status = autoTransitionMapper.selectStatusForUpdate(basecampId);
        if (!isBeforeConfirm(status)) {
            return false;
        }
        boolean enough = autoTransitionMapper.countActiveMembers(basecampId) >= Basecamp.MIN_CONFIRM_HEADCOUNT;
        int changed = enough
                ? autoTransitionMapper.confirmIfBeforeConfirm(basecampId, now)
                : autoTransitionMapper.cancelForNotEnoughMembers(basecampId, now);
        if (changed == 0) {
            return false;
        }
        autoTransitionMapper.expirePendingApplications(basecampId, now);
        String eventType =
                enough ? BasecampTransitionEvents.CONFIRMED_EVENT_TYPE : BasecampTransitionEvents.CANCELED_EVENT_TYPE;
        recordTransition(eventType, basecampId);
        return true;
    }

    /**
     * 호출하면 종료일이 today보다 앞선 확정 베이스캠프를 완료로 바꾸고, 결정되지 않은 신청을 만료시킨다.
     * 끝까지 남은 ACTIVE 멤버(캠프 리더 포함, 탈퇴·강퇴한 멤버 제외)에게 알릴 이벤트를 같은 트랜잭션에서 기록한다.
     * 다른 쪽이 이미 처리했거나 아직 종료일이 지나지 않았으면 아무것도 바꾸지 않고 false를 돌려준다.
     */
    @Transactional
    public boolean complete(long basecampId, LocalDate today, Instant now) {
        if (autoTransitionMapper.completeIfConfirmed(basecampId, today, now) == 0) {
            return false;
        }
        autoTransitionMapper.expirePendingApplications(basecampId, now);
        recordTransition(BasecampTransitionEvents.COMPLETED_EVENT_TYPE, basecampId);
        return true;
    }

    private static boolean isBeforeConfirm(String status) {
        return BasecampStatus.RECRUITING.name().equals(status)
                || BasecampStatus.CLOSED.name().equals(status);
    }

    private void recordTransition(String eventType, long basecampId) {
        List<Long> memberIds = autoTransitionMapper.selectActiveMemberIds(basecampId);
        outboxEventRecorder.record(
                eventType,
                BasecampTransitionEvents.AGGREGATE_TYPE,
                basecampId,
                new BasecampTransitionEvents.Payload(basecampId, memberIds));
    }
}
