package com.pitchmap.program.application;

import com.pitchmap.common.audit.AdminAuditAction;
import com.pitchmap.common.audit.AdminAuditRecorder;
import com.pitchmap.common.audit.AdminAuditTargetType;
import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.outbox.OutboxEventRecorder;
import com.pitchmap.program.domain.PaymentRepository;
import com.pitchmap.program.domain.Program;
import com.pitchmap.program.domain.ProgramApplication;
import com.pitchmap.program.domain.ProgramApplicationRepository;
import com.pitchmap.program.domain.ProgramApplicationStatus;
import com.pitchmap.program.domain.ProgramCancelReason;
import com.pitchmap.program.domain.ProgramChange;
import com.pitchmap.program.domain.ProgramDetails;
import com.pitchmap.program.domain.ProgramRepository;
import com.pitchmap.program.domain.ProgramRevision;
import com.pitchmap.program.infra.ProgramApplicantRow;
import com.pitchmap.program.infra.ProgramQueryMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자가 공식 행사를 등록, 수정, 취소하고 신청자 목록을 읽는다.
 *
 * <p>등록, 수정, 취소는 감사 로그를 같은 트랜잭션에서 남긴다. 수정과 취소는 첫 쿼리로 행사 행을 쓰기 잠금으로 읽는다.
 * 그래서 같은 행사를 동시에 고치거나 취소하면 뒤 요청은 잠금을 기다린 뒤 앞 요청이 커밋한 상태를 보고 판단한다.
 * 취소하는 동안에는 새 신청도 같은 행 잠금을 기다리므로, 취소 직후 이 행사에 활성 신청이 남지 않는다.
 *
 * <p>행사가 없으면 NOT_FOUND, 값의 범위나 시각 순서가 틀리거나 연결하려는 장소가 지도에 보이지 않으면 INVALID_INPUT으로 거부한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramAdminService {

    private final ProgramRepository programRepository;
    private final ProgramApplicationRepository programApplicationRepository;
    private final PaymentRepository paymentRepository;
    private final ProgramQueryMapper programQueryMapper;
    private final ProgramQueryService programQueryService;
    private final AdminAuditRecorder adminAuditRecorder;
    private final OutboxEventRecorder outboxEventRecorder;
    private final Clock clock;

    /** 호출하면 adminId인 관리자가 command대로 행사를 등록하고 그 행사의 ID를 돌려준다. */
    @Transactional
    public long create(long adminId, ProgramCreateCommand command) {
        Program program = newProgram(adminId, command, clock.instant());
        requireVisibleSpot(command.spotId());
        Program saved = programRepository.saveAndFlush(program);
        adminAuditRecorder.record(
                adminId,
                AdminAuditAction.PROGRAM_CREATE,
                AdminAuditTargetType.PROGRAM,
                saved.getId(),
                new ProgramAuditDetails.Create(saved.getCapacity(), saved.isOvernight()));
        log.info("program created programId={} adminId={}", saved.getId(), adminId);
        return saved.getId();
    }

    /**
     * 호출하면 programId인 행사를 command에서 null이 아닌 필드로 고치고, 고친 뒤의 상세를 돌려준다(myApplication은 없다).
     * 취소된 행사는 PROGRAM_INVALID_STATE, 신청 시작 뒤의 정원 축소는 PROGRAM_CAPACITY_DECREASE로 거부한다.
     */
    @Transactional
    public ProgramDetail revise(long adminId, long programId, ProgramReviseCommand command) {
        Program program = lockProgram(programId);
        if (command.spotId().present()) {
            requireVisibleSpot(command.spotId().value());
        }
        ProgramChange change = revise(program, command, clock.instant());
        programRepository.saveAndFlush(program);
        adminAuditRecorder.record(
                adminId,
                AdminAuditAction.PROGRAM_UPDATE,
                AdminAuditTargetType.PROGRAM,
                programId,
                updateDetail(change));
        log.info("program revised programId={} adminId={}", programId, adminId);
        // 위에서 플러시했으므로 MyBatis가 고친 내용을 읽는다.
        return programQueryService.detail(programId, null);
    }

    /**
     * 호출하면 programId인 행사를 취소하고, 결제 대기·확정 신청을 모두 취소 사유 PROGRAM_CANCELED로 바꾸며, 결제 완료 건을 환불 처리한다.
     * 이미 취소된 행사는 PROGRAM_INVALID_STATE로 거부한다. 취소된 신청마다 신청자에게 알릴 이벤트(환불 여부 포함)를 같은 트랜잭션에서 기록한다.
     *
     * <p>일괄 UPDATE 전에 활성 신청을 쓰기 잠금으로 읽는다. 결제가 진행 중이면 그 커밋을 기다린 뒤에 읽으므로, 이벤트의 환불 여부가
     * 일괄 UPDATE가 실제로 환불한 결제와 같다.
     */
    @Transactional
    public ProgramCancelResult cancel(long adminId, long programId) {
        Program program = lockProgram(programId);
        Instant now = clock.instant();
        String fromStatus = program.getStatus().name();
        program.cancel(now);
        List<ProgramApplication> activeApplications =
                programApplicationRepository.findActiveByProgramForUpdate(programId);
        Set<Long> paidApplicationIds = new HashSet<>(paymentRepository.findPaidApplicationIdsByProgram(programId));
        int canceledApplicationCount = programApplicationRepository.cancelActiveByProgram(
                programId, ProgramCancelReason.PROGRAM_CANCELED, now);
        int refundedPaymentCount = paymentRepository.refundPaidByProgram(programId, now);
        recordCanceledEvents(programId, activeApplications, paidApplicationIds);
        adminAuditRecorder.record(
                adminId,
                AdminAuditAction.PROGRAM_CANCEL,
                AdminAuditTargetType.PROGRAM,
                programId,
                new ProgramAuditDetails.Cancel(
                        fromStatus, program.getStatus().name(), canceledApplicationCount, refundedPaymentCount));
        log.info(
                "program canceled programId={} adminId={} canceledApplicationCount={} refundedPaymentCount={}",
                programId,
                adminId,
                canceledApplicationCount,
                refundedPaymentCount);
        return new ProgramCancelResult(programId, program.getStatus().name(), canceledApplicationCount);
    }

    // 신청자마다 이벤트를 하나씩 기록한다. 결제를 마친 신청은 환불했다고 알린다.
    private void recordCanceledEvents(
            long programId, List<ProgramApplication> applications, Set<Long> paidApplicationIds) {
        for (ProgramApplication application : applications) {
            long applicationId = application.getId();
            outboxEventRecorder.record(
                    ProgramApplicationEvents.CANCELED_EVENT_TYPE,
                    ProgramApplicationEvents.AGGREGATE_TYPE,
                    applicationId,
                    new ProgramApplicationEvents.CanceledPayload(
                            applicationId,
                            application.getMemberId(),
                            programId,
                            ProgramCancelReason.PROGRAM_CANCELED.name(),
                            paidApplicationIds.contains(applicationId)));
        }
    }

    /**
     * 호출하면 programId인 행사의 신청을 query.status로 거르고 신청한 순서로 한 페이지 읽는다. 행사가 없으면 NOT_FOUND로 실패한다.
     * 다음 페이지가 있는지 알려고 한 건을 더 읽고, 그 건은 결과에서 뺀다.
     */
    @Transactional(readOnly = true)
    public ProgramApplicantPage applications(long programId, ProgramApplicantQuery query) {
        programRepository.findById(programId).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        ProgramApplicationStatus status = parseStatus(query.status());
        long offset = (long) query.page() * query.size();
        List<ProgramApplicantRow> rows =
                programQueryMapper.selectApplicants(programId, status, offset, query.size() + 1);
        boolean hasNext = rows.size() > query.size();
        List<ProgramApplicantItem> content = rows.stream()
                .limit(query.size())
                .map(ProgramAdminService::toItem)
                .toList();
        return new ProgramApplicantPage(content, query.page(), query.size(), hasNext);
    }

    // 모르는 상태 이름은 서버가 조용히 무시하지 않고 입력 오류로 거부한다.
    private static ProgramApplicationStatus parseStatus(String status) {
        if (status == null) {
            return null;
        }
        try {
            return ProgramApplicationStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "status: 신청 상태가 올바르지 않습니다.");
        }
    }

    private Program lockProgram(long programId) {
        return programRepository
                .findByIdForUpdate(programId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    // 장소가 없거나 지도에 보이지 않는 상태이면 연결하지 않는다. spotId가 null이면 연결하지 않는 행사라서 검사하지 않는다.
    private void requireVisibleSpot(Long spotId) {
        if (spotId != null && !programQueryMapper.existsActiveSpot(spotId)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "spotId: 지도에 보이는 장소만 연결할 수 있습니다.");
        }
    }

    // 범위나 시각 순서 위반처럼 도메인이 IllegalArgumentException으로 알리는 요청 값 오류는 INVALID_INPUT으로 바꾼다.
    private static Program newProgram(long adminId, ProgramCreateCommand command, Instant now) {
        int paymentDeadlineMinutes = command.paymentDeadlineMinutes() == null
                ? Program.DEFAULT_PAYMENT_DEADLINE_MINUTES
                : command.paymentDeadlineMinutes();
        ProgramDetails details = new ProgramDetails(
                command.title(),
                command.description(),
                command.spotId(),
                command.locationText(),
                command.startAt(),
                command.endAt(),
                command.capacity(),
                command.fee(),
                command.applyOpenAt(),
                command.applyCloseAt(),
                paymentDeadlineMinutes,
                command.overnight());
        try {
            return Program.create(adminId, details, now);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, e.getMessage());
        }
    }

    private static ProgramChange revise(Program program, ProgramReviseCommand command, Instant now) {
        ProgramRevision revision = new ProgramRevision(
                command.title(),
                command.description(),
                command.spotId(),
                command.locationText(),
                command.startAt(),
                command.endAt(),
                command.capacity(),
                command.fee(),
                command.applyOpenAt(),
                command.applyCloseAt(),
                command.paymentDeadlineMinutes(),
                command.overnight());
        try {
            return program.revise(revision, now);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, e.getMessage());
        }
    }

    private static ProgramAuditDetails.Update updateDetail(ProgramChange change) {
        if (!change.isCapacityChanged()) {
            return new ProgramAuditDetails.Update(change.changedFields(), null, null);
        }
        return new ProgramAuditDetails.Update(change.changedFields(), change.capacityBefore(), change.capacityAfter());
    }

    private static ProgramApplicantItem toItem(ProgramApplicantRow row) {
        return new ProgramApplicantItem(
                row.applicationId(),
                new ProgramApplicantItem.Applicant(row.memberId(), row.nickname()),
                row.status().name(),
                row.paymentDueAt(),
                row.confirmedAt(),
                row.canceledAt(),
                row.cancelReason() == null ? null : row.cancelReason().name(),
                row.reviewRequestedAt(),
                row.createdAt());
    }
}
