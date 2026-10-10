package com.pitchmap.program.application;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.outbox.OutboxEventRecorder;
import com.pitchmap.program.domain.Payment;
import com.pitchmap.program.domain.PaymentRepository;
import com.pitchmap.program.domain.Program;
import com.pitchmap.program.domain.ProgramApplication;
import com.pitchmap.program.domain.ProgramApplicationRepository;
import com.pitchmap.program.domain.ProgramApplicationStatus;
import com.pitchmap.program.domain.ProgramCancelReason;
import com.pitchmap.program.domain.ProgramErrorCode;
import com.pitchmap.program.domain.ProgramException;
import com.pitchmap.program.domain.ProgramRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신청자가 가짜 결제로 신청을 확정하거나 신청을 취소한다.
 *
 * <p>두 작업 모두 트랜잭션의 첫 쿼리로 신청 행을 쓰기 잠금으로 읽는다. 같은 신청을 동시에 결제하거나 취소하면 뒤 요청은 잠금을 기다린 뒤
 * 앞 요청이 커밋한 상태를 보고 판단한다. 행사 행은 잠그지 않고 읽기만 한다. 관리자의 행사 취소는 행사 행을 잠근 다음 신청 행을 잠그므로,
 * 이 서비스가 행사 행을 잠그지 않아야 두 작업 사이에 잠금 순환이 생기지 않는다.
 *
 * <p>검사는 신청이 없으면 NOT_FOUND, 남의 신청이면 ACCESS_DENIED, 그다음 신청 상태 순서로 하고 처음 걸린 오류 하나만 던진다.
 * 확정과 취소는 알림 이벤트를 같은 트랜잭션에서 기록하므로, 롤백되면 알림도 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramPaymentService {

    private final ProgramApplicationRepository programApplicationRepository;
    private final ProgramRepository programRepository;
    private final PaymentRepository paymentRepository;
    private final OutboxEventRecorder outboxEventRecorder;
    private final ProgramSeatReleaseRecorder seatReleaseRecorder;
    private final Clock clock;

    /**
     * 호출하면 memberId인 회원의 결제 대기 신청을 확정하고 결제를 기록한다. 결제 금액은 결제하는 시점의 행사 참가비이고 0원이어도 결제 행을 만든다.
     * 결제 기한 정각부터는 기한이 지난 것으로 본다.
     *
     * <p>이미 결제 행이 있어 저장이 유니크 제약에 걸리면 PROGRAM_INVALID_STATE로 바꿔 던진다. 신청 행 잠금 때문에 보통은 일어나지 않는다.
     */
    @Transactional
    public ProgramPayResult pay(long memberId, long applicationId) {
        ProgramApplication application = lockOwnApplication(memberId, applicationId);
        Instant now = clock.instant();
        application.confirmPayment(now);
        Program program = findProgram(application.getProgramId());
        savePayment(Payment.paid(applicationId, program.getFee(), now));
        outboxEventRecorder.record(
                ProgramApplicationEvents.CONFIRMED_EVENT_TYPE,
                ProgramApplicationEvents.AGGREGATE_TYPE,
                applicationId,
                new ProgramApplicationEvents.ConfirmedPayload(applicationId, memberId, application.getProgramId()));
        log.info("program payment confirmed applicationId={} memberId={}", applicationId, memberId);
        return new ProgramPayResult(applicationId, ProgramApplicationStatus.CONFIRMED.name(), now);
    }

    /**
     * 호출하면 memberId인 회원의 신청을 취소한다. 결제를 마친 신청이면 결제도 환불 처리하고, 환불했는지를 돌려준다.
     * 취소로 상태가 CANCELED가 되면 그 신청은 정원을 차지하지 않으므로 자리가 돌아간다.
     */
    @Transactional
    public ProgramApplicationCancelResult cancel(long memberId, long applicationId) {
        ProgramApplication application = lockOwnApplication(memberId, applicationId);
        Instant now = clock.instant();
        Program program = findProgram(application.getProgramId());
        boolean refunded = application.cancelByMember(now, program.refundDeadline());
        if (refunded) {
            refundPayment(applicationId, now);
        }
        outboxEventRecorder.record(
                ProgramApplicationEvents.CANCELED_EVENT_TYPE,
                ProgramApplicationEvents.AGGREGATE_TYPE,
                applicationId,
                new ProgramApplicationEvents.CanceledPayload(
                        applicationId,
                        memberId,
                        application.getProgramId(),
                        ProgramCancelReason.USER.name(),
                        refunded));
        seatReleaseRecorder.record(program, now);
        log.info(
                "program application canceled applicationId={} memberId={} refunded={}",
                applicationId,
                memberId,
                refunded);
        return new ProgramApplicationCancelResult(ProgramApplicationStatus.CANCELED.name(), refunded);
    }

    private ProgramApplication lockOwnApplication(long memberId, long applicationId) {
        ProgramApplication application = programApplicationRepository
                .findByIdForUpdate(applicationId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (application.getMemberId() != memberId) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
        return application;
    }

    private Program findProgram(long programId) {
        return programRepository
                .findById(programId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private void savePayment(Payment payment) {
        try {
            paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            // 신청 행 잠금 때문에 보통은 일어나지 않는다. 그래도 DB의 유니크 제약이 던진 예외를 500으로 내보내지 않는다.
            throw new ProgramException(ProgramErrorCode.PROGRAM_INVALID_STATE);
        }
    }

    private void refundPayment(long applicationId, Instant now) {
        Payment payment = paymentRepository
                .findByProgramApplicationId(applicationId)
                .orElseThrow(() -> new IllegalStateException("확정된 신청에 결제가 없습니다. applicationId=" + applicationId));
        payment.refund(now);
    }
}
