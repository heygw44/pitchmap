package com.pitchmap.basecamp.application;

import com.pitchmap.basecamp.domain.RemovalCause;
import com.pitchmap.common.outbox.OutboxEventHandler;
import com.pitchmap.common.outbox.OutboxMessage;
import org.springframework.stereotype.Component;

/**
 * 회원 탈퇴 뒤에 오는 베이스캠프 정리 이벤트를 받아, 탈퇴한 회원을 진행 중인 베이스캠프에서 정리한다.
 * 제재 정리 처리기와 같은 서비스를 쓰고, 정리하는 이유만 탈퇴로 넘긴다.
 *
 * <p>이 클래스에는 {@code @Transactional}을 걸지 않는다. 베이스캠프마다 자기 트랜잭션으로 처리하는 일은
 * {@link BasecampSanctionCleanupService}가 맡는다. 정리에 실패하면 예외가 발행기로 올라가 이벤트를 다시 처리한다.
 */
@Component
class WithdrawalBasecampCleanupHandler implements OutboxEventHandler {

    // 이벤트를 기록하는 trust 모듈의 상수와 같은 값이다. basecamp는 trust 모듈의 클래스를 가져다 쓰지 않으므로 문자열을 따로 둔다.
    static final String EVENT_TYPE = "WITHDRAWAL_BASECAMP_CLEANUP";

    private final BasecampSanctionCleanupService cleanupService;

    WithdrawalBasecampCleanupHandler(BasecampSanctionCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    // 이벤트의 aggregate ID가 탈퇴한 회원 ID라서 payload는 읽지 않는다.
    @Override
    public void handle(OutboxMessage message) {
        cleanupService.cleanUp(message.aggregateId(), RemovalCause.WITHDRAWAL);
    }
}
