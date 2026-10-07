package com.pitchmap.basecamp.application;

import com.pitchmap.basecamp.domain.Basecamp;
import com.pitchmap.basecamp.domain.BasecampRepository;
import com.pitchmap.basecamp.domain.JoinEligibilityPolicy;
import com.pitchmap.basecamp.domain.KickReason;
import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.outbox.OutboxEventRecorder;
import com.pitchmap.trust.application.TrustSummaryService;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 멤버가 스스로 탈퇴하거나 캠프 리더가 멤버를 강퇴한다.
 *
 * <p>같은 멤버에 대한 강퇴와 탈퇴, 빈자리를 두고 겹치는 승인이 서로 어긋나지 않도록, 베이스캠프 행을 쓰기 잠금으로 읽어
 * 같은 베이스캠프의 쓰기를 한 번에 하나씩 처리한다.
 */
@Service
@RequiredArgsConstructor
public class BasecampMembershipService {

    private final BasecampRepository basecampRepository;
    private final TrustSummaryService trustSummaryService;
    private final OutboxEventRecorder outboxEventRecorder;
    private final Clock clock;

    /**
     * 호출하면 memberId인 멤버가 basecampId인 베이스캠프에서 탈퇴한다. 탈퇴한 회원은 같은 베이스캠프에 다시 신청할 수 없다.
     * 확정된 뒤 출발 48시간 안에 하는 탈퇴는 임박 탈퇴로 기록하고, 정원이 차서 자동 마감된 베이스캠프는 빈자리가 생기면 다시 모집한다.
     *
     * <p>다음 순서로 검사하고 처음 걸린 이유로 거부한다.
     * <ol>
     *   <li>베이스캠프가 없으면 NOT_FOUND
     *   <li>신뢰 단계가 1 미만이면 TRUST_LEVEL_INSUFFICIENT
     *   <li>베이스캠프가 완료되었거나 취소되었으면 BASECAMP_INVALID_STATE
     *   <li>ACTIVE 멤버가 아니면 NOT_FOUND
     *   <li>캠프 리더이면 BASECAMP_LEADER_CANNOT_LEAVE
     * </ol>
     */
    @Transactional
    public void leave(long basecampId, long memberId) {
        Basecamp basecamp = lock(basecampId);
        if (trustSummaryService.detail(memberId).trustLevel() < JoinEligibilityPolicy.BASE_TRUST_LEVEL) {
            throw new BusinessException(CommonErrorCode.TRUST_LEVEL_INSUFFICIENT);
        }
        basecamp.leave(memberId, clock.instant());
    }

    /**
     * 호출하면 leaderId인 캠프 리더가 targetMemberId인 멤버를 reason을 남기고 강퇴하고, 강퇴된 회원에게 알릴 이벤트를 같은 트랜잭션에서 기록한다.
     * 강퇴된 회원은 같은 베이스캠프에 다시 신청할 수 없다.
     *
     * <p>다음 순서로 검사하고 처음 걸린 이유로 거부한다.
     * <ol>
     *   <li>베이스캠프가 없으면 NOT_FOUND
     *   <li>요청자가 캠프 리더가 아니면 ACCESS_DENIED
     *   <li>베이스캠프가 확정되었거나 그 뒤 상태이면 BASECAMP_INVALID_STATE
     *   <li>대상이 ACTIVE 멤버가 아니면 NOT_FOUND
     *   <li>대상이 캠프 리더이면 BASECAMP_LEADER_CANNOT_LEAVE
     * </ol>
     */
    @Transactional
    public void kick(long basecampId, long targetMemberId, long leaderId, KickReason reason) {
        Basecamp basecamp = lock(basecampId);
        if (!basecamp.isLeader(leaderId)) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
        basecamp.kick(targetMemberId, reason, clock.instant());
        outboxEventRecorder.record(
                BasecampMembershipEvents.KICKED_EVENT_TYPE,
                BasecampMembershipEvents.AGGREGATE_TYPE,
                basecamp.getId(),
                new BasecampMembershipEvents.KickedPayload(basecamp.getId(), targetMemberId, reason.name()));
    }

    // 베이스캠프 행의 잠금이 이 트랜잭션의 첫 조회여야 한다. MySQL의 반복 가능한 읽기에서는 잠그지 않는 조회가 먼저 일어나면
    // 그 시점에 읽기 스냅숏이 정해져서, 잠금을 얻은 뒤에도 먼저 끝난 다른 트랜잭션이 커밋한 변경이 보이지 않는다.
    private Basecamp lock(long basecampId) {
        return basecampRepository
                .findByIdForUpdate(basecampId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
