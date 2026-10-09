package com.pitchmap.program.application;

import com.pitchmap.common.outbox.OutboxEventRecorder;
import com.pitchmap.program.infra.ExpiryTarget;
import com.pitchmap.program.infra.ProgramPaymentExpiryMapper;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 기한이 지난 신청 하나를 만료로 바꾼다. 메서드 하나가 트랜잭션 하나이고 신청 행 하나만 잠근다.
 *
 * <p>여러 신청을 한 문장으로 일괄 UPDATE하지 않는 이유가 이것이다. 일괄 UPDATE는 신청 행 여러 개를 한꺼번에 잠그는데,
 * 관리자의 행사 취소는 행사 행을 먼저 잠그고 신청 행을 ID 순서로 잠그고, 선착순 신청은 행사 행을 쓰기 잠금으로 잡은 채 신청 행을
 * 공유 잠금으로 읽는다. 여러 행을 잡은 쪽이 이 순서와 엇갈리면 서로 기다리는 잠금 순환이 생길 수 있다. 신청 하나씩 처리하면
 * 이 트랜잭션은 신청 행 하나만 잡으므로 순환이 생기지 않는다.
 *
 * <p>결제, 본인 취소, 행사 취소, 다른 서버의 만료와 겹쳐도 한 번만 처리하도록 엔티티를 읽지 않고 상태와 기한을 건 UPDATE로 바꾼다.
 * 다른 트랜잭션이 같은 행을 잠그고 있으면 UPDATE가 기다린 뒤 최신 커밋 값으로 조건을 다시 평가한다.
 * 그래서 결제가 먼저 확정했다면 영향받은 행이 0이고 이 메서드는 아무것도 기록하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProgramPaymentExpiryApplier {

    private final ProgramPaymentExpiryMapper expiryMapper;
    private final OutboxEventRecorder outboxEventRecorder;

    /**
     * 호출하면 결제 기한이 now 이하인 결제 대기 신청을 만료로 바꾸고, 신청자에게 알릴 이벤트를 같은 트랜잭션에서 기록한 뒤 true를 돌려준다.
     * 신청의 상태가 이미 바뀌었거나 기한이 아직 지나지 않았으면 아무것도 바꾸지 않고 false를 돌려준다.
     */
    @Transactional
    public boolean expire(ExpiryTarget target, Instant now) {
        if (expiryMapper.expireIfDue(target.applicationId(), now) == 0) {
            return false;
        }
        outboxEventRecorder.record(
                ProgramApplicationEvents.EXPIRED_EVENT_TYPE,
                ProgramApplicationEvents.AGGREGATE_TYPE,
                target.applicationId(),
                new ProgramApplicationEvents.ExpiredPayload(
                        target.applicationId(), target.memberId(), target.programId()));
        log.info("program application expired applicationId={} memberId={}", target.applicationId(), target.memberId());
        return true;
    }
}
