package com.pitchmap.program.application;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.outbox.OutboxEventRecorder;
import com.pitchmap.program.domain.Program;
import com.pitchmap.program.domain.ProgramApplication;
import com.pitchmap.program.domain.ProgramApplicationRepository;
import com.pitchmap.program.domain.ProgramApplicationStatus;
import com.pitchmap.program.domain.ProgramCancelReason;
import com.pitchmap.program.domain.ProgramRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제재를 받은 회원의 행사 신청을 정리한다. 메서드 하나가 트랜잭션 하나이고, 결제 대기 신청은 한 건씩 신청 행 하나만 잠가 처리한다.
 * 그래서 한 신청의 처리가 실패해도 다른 신청의 처리는 그대로 커밋된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProgramSanctionCleanupApplier {

    private final ProgramApplicationRepository applicationRepository;
    private final ProgramRepository programRepository;
    private final ProgramSeatReleaseRecorder seatReleaseRecorder;
    private final OutboxEventRecorder outboxEventRecorder;
    private final Clock clock;

    /**
     * 호출하면 applicationId인 결제 대기 신청을 취소(사유 SANCTIONED)하고, 신청자에게 알릴 이벤트와 돌아온 자리를 알릴 이벤트를
     * 같은 트랜잭션에서 기록한 뒤 true를 돌려준다. 결제를 마치거나 만료되거나 취소되는 등 결제 대기가 아니면 아무것도 바꾸지 않고 false를 돌려준다.
     */
    @Transactional
    public boolean cancelPending(long applicationId, long memberId) {
        // 신청 행만 잠그고 행사 행은 잠그지 않는다. 본인 취소와 결제 만료도 신청 행만 잡으므로 잠그는 순서가 엇갈리지 않는다.
        ProgramApplication application =
                applicationRepository.findByIdForUpdate(applicationId).orElse(null);
        if (application == null || application.getStatus() != ProgramApplicationStatus.PENDING_PAYMENT) {
            return false;
        }
        Instant now = clock.instant();
        application.cancelBySanction(now);
        outboxEventRecorder.record(
                ProgramApplicationEvents.CANCELED_EVENT_TYPE,
                ProgramApplicationEvents.AGGREGATE_TYPE,
                applicationId,
                new ProgramApplicationEvents.CanceledPayload(
                        applicationId,
                        memberId,
                        application.getProgramId(),
                        ProgramCancelReason.SANCTIONED.name(),
                        false));
        // 행사 행은 잠그지 않고 읽기만 한다. 관리자의 행사 취소가 행사 행을 먼저 잠그므로 여기서 잠그면 잠금 순환이 생길 수 있다.
        Program program = programRepository
                .findById(application.getProgramId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        seatReleaseRecorder.record(program, now);
        log.info("program application canceled by sanction applicationId={} memberId={}", applicationId, memberId);
        return true;
    }

    /**
     * 호출하면 memberId인 회원의 확정 신청 중 확인 요청 시각이 없는 신청에 지금 시각을 적고, 표시한 신청 수를 돌려준다.
     * 결제를 마친 신청은 취소하지 않는다. 환불과 취소 여부는 관리자가 정한다.
     */
    @Transactional
    public int requestReview(long memberId) {
        return applicationRepository.requestReviewForConfirmed(memberId, clock.instant());
    }
}
