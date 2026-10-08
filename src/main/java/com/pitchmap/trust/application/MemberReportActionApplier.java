package com.pitchmap.trust.application;

import com.pitchmap.common.audit.AdminAuditAction;
import com.pitchmap.common.audit.AdminAuditRecorder;
import com.pitchmap.common.audit.AdminAuditTargetType;
import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.outbox.OutboxEventRecorder;
import com.pitchmap.trust.domain.CompanionReview;
import com.pitchmap.trust.domain.CompanionReviewRepository;
import com.pitchmap.trust.domain.MemberReport;
import com.pitchmap.trust.domain.MemberReportRepository;
import com.pitchmap.trust.domain.ReportKind;
import com.pitchmap.trust.domain.ReportStatus;
import com.pitchmap.trust.domain.Sanction;
import com.pitchmap.trust.domain.SanctionRepository;
import com.pitchmap.trust.domain.SanctionType;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신고 조치에서 트랜잭션 하나로 묶는 부분이다. 제재 확정, 후기 숨김, 신고 상태 변경, 결과 이벤트, 감사 로그를 함께 커밋하거나
 * 함께 되돌린다. 세션 삭제는 {@link MemberReportActionService}가 이 트랜잭션이 끝난 뒤에 한다.
 */
@Service
@RequiredArgsConstructor
public class MemberReportActionApplier {

    private final MemberReportRepository memberReportRepository;
    private final CompanionReviewRepository companionReviewRepository;
    private final SanctionRepository sanctionRepository;
    private final SanctionConfirmApplier sanctionConfirmApplier;
    private final OutboxEventRecorder outboxEventRecorder;
    private final AdminAuditRecorder adminAuditRecorder;
    private final Clock clock;

    /**
     * 호출하면 검토 중인 신고를 조치 완료로 바꾼다. 요청에 제재가 있으면 신고 대상 회원에게 확정하고, hideReview이면 신고된 동행 후기를 숨긴다.
     * 신고 행을 쓰기 잠금으로 먼저 읽고, 제재 확정이 그다음에 회원 행을 잠근다. 이 순서를 모든 신고 처리가 지켜서 데드락을 피한다.
     * 긴급 신고의 임시 정지는 건드리지 않고 자연 만료되게 둔다.
     *
     * <p>다음 순서로 검사하고 처음 걸린 이유로 거부한다.
     * <ol>
     *   <li>신고가 없으면 NOT_FOUND
     *   <li>신고가 검토 중이 아니면 REPORT_INVALID_STATE
     *   <li>제재도 후기 숨김도 요청하지 않았거나, 회원 신고에 후기 숨김을 요청했으면 INVALID_INPUT
     *   <li>요청한 제재 종류가 회원의 다음 단계와 다르면 INVALID_INPUT
     * </ol>
     */
    @Transactional
    public MemberReportActionResult apply(MemberReportActionCommand command, SanctionType sanctionType) {
        MemberReport report = memberReportRepository
                .findByIdForUpdate(command.reportId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        report.requireInReview();
        requireConsistentRequest(report, command, sanctionType);
        Instant now = clock.instant();
        SanctionConfirmResult confirmed = sanctionType == null ? null : confirmSanction(report, command, sanctionType);
        if (command.hideReview()) {
            hideReview(report, now);
        }
        ReportStatus before = report.getStatus();
        report.action(command.adminId(), command.note(), now);
        outboxEventRecorder.record(
                MemberReportEvents.RESOLVED_EVENT_TYPE,
                MemberReportEvents.AGGREGATE_TYPE,
                report.getId(),
                new MemberReportEvents.ResolvedPayload(
                        report.getId(), report.getReporterId(), ReportStatus.ACTIONED.name()));
        Long sanctionId = confirmed == null ? null : confirmed.sanctionId();
        recordAudit(report, before, command, sanctionId);
        return new MemberReportActionResult(
                report.getId(),
                report.getStatus().name(),
                sanctionId,
                report.getTargetMemberId(),
                confirmed != null && confirmed.suspended());
    }

    private static void requireConsistentRequest(
            MemberReport report, MemberReportActionCommand command, SanctionType sanctionType) {
        if (sanctionType == null && !command.hideReview()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "제재를 내리거나 후기를 숨기는 요청이 하나는 있어야 합니다.");
        }
        if (command.hideReview() && report.getKind() != ReportKind.REVIEW) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "후기 신고만 후기를 숨길 수 있습니다.");
        }
    }

    private SanctionConfirmResult confirmSanction(
            MemberReport report, MemberReportActionCommand command, SanctionType sanctionType) {
        return sanctionConfirmApplier.apply(new SanctionConfirmCommand(
                report.getTargetMemberId(), report.getId(), sanctionType, command.sanctionReason(), command.adminId()));
    }

    private void hideReview(MemberReport report, Instant now) {
        CompanionReview review = companionReviewRepository
                .findById(report.getCompanionReviewId())
                .orElseThrow(() -> new IllegalStateException("신고된 동행 후기가 없습니다. reportId=" + report.getId()));
        review.hide(now);
    }

    private void recordAudit(
            MemberReport report, ReportStatus before, MemberReportActionCommand command, Long sanctionId) {
        Sanction sanction = sanctionId == null
                ? null
                : sanctionRepository
                        .findById(sanctionId)
                        .orElseThrow(() -> new IllegalStateException("방금 확정한 제재가 없습니다. sanctionId=" + sanctionId));
        Integer level = sanction == null || sanction.getLevel() == null
                ? null
                : sanction.getLevel().intValue();
        adminAuditRecorder.record(
                command.adminId(),
                AdminAuditAction.REPORT_ACTION,
                AdminAuditTargetType.MEMBER_REPORT,
                report.getId(),
                new TrustAuditDetails.Action(
                        before.name(),
                        report.getStatus().name(),
                        command.hideReview(),
                        sanctionId,
                        sanction == null ? null : sanction.getType().name(),
                        level));
    }
}
