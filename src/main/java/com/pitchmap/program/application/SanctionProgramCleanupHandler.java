package com.pitchmap.program.application;

import com.pitchmap.common.outbox.OutboxEventHandler;
import com.pitchmap.common.outbox.OutboxMessage;
import com.pitchmap.program.domain.ProgramCancelReason;
import org.springframework.stereotype.Component;

/**
 * 제재 확정 뒤에 오는 행사 신청 정리 이벤트를 받아, 제재를 받은 회원의 행사 신청을 정리한다.
 *
 * <p>이 클래스에는 {@code @Transactional}을 걸지 않는다. 신청마다 자기 트랜잭션으로 처리하는 일은
 * {@link ProgramSanctionCleanupService}가 맡는다. 정리에 실패하면 예외가 발행기로 올라가 이벤트를 다시 처리한다.
 */
@Component
class SanctionProgramCleanupHandler implements OutboxEventHandler {

    // 이벤트를 기록하는 trust 모듈의 상수와 같은 값이다. program은 trust 모듈의 클래스를 가져다 쓰지 않으므로 문자열을 따로 둔다.
    static final String EVENT_TYPE = "SANCTION_PROGRAM_CLEANUP";

    private final ProgramSanctionCleanupService cleanupService;

    SanctionProgramCleanupHandler(ProgramSanctionCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    // 이벤트의 aggregate ID가 제재를 받은 회원 ID라서 payload는 읽지 않는다.
    @Override
    public void handle(OutboxMessage message) {
        cleanupService.cleanUp(message.aggregateId(), ProgramCancelReason.SANCTIONED);
    }
}
