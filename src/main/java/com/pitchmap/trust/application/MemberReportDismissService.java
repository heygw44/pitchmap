package com.pitchmap.trust.application;

import com.pitchmap.common.audit.AdminAuditAction;
import com.pitchmap.common.audit.AdminAuditRecorder;
import com.pitchmap.common.audit.AdminAuditTargetType;
import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.outbox.OutboxEventRecorder;
import com.pitchmap.member.application.MemberSuspensionService;
import com.pitchmap.trust.domain.MemberReport;
import com.pitchmap.trust.domain.MemberReportRepository;
import com.pitchmap.trust.domain.ReportStatus;
import com.pitchmap.trust.domain.Sanction;
import com.pitchmap.trust.domain.SanctionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자가 신고를 기각한다. 처리 메모는 로그에 남기지 않는다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberReportDismissService {

    private final MemberReportRepository memberReportRepository;
    private final SanctionRepository sanctionRepository;
    private final MemberSuspensionService memberSuspensionService;
    private final SuspensionResyncer suspensionResyncer;
    private final OutboxEventRecorder outboxEventRecorder;
    private final AdminAuditRecorder adminAuditRecorder;
    private final Clock clock;

    /**
     * 호출하면 검토 중인 신고를 기각한다. 그 신고가 만든 임시 정지가 적용 중이면 해제하고, 회원의 정지 상태를 남은 정지에 맞춰 다시 정한다.
     * 같은 회원에게 다른 임시 정지나 확정 정지가 남아 있으면 회원은 정지 상태로 남는다. 세션은 지우지 않는다. 풀리는 쪽이라 막을 것이 없다.
     *
     * <p>신고 행, 회원 행, 제재 행 순서로 잠근다. 신고가 없으면 NOT_FOUND, 검토 중이 아니면 REPORT_INVALID_STATE로 거부한다.
     */
    @Transactional
    public MemberReportStatusResult dismiss(long reportId, long adminId, String note) {
        MemberReport report = memberReportRepository
                .findByIdForUpdate(reportId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        report.requireInReview();
        Instant now = clock.instant();
        memberSuspensionService.lockForSanction(report.getTargetMemberId());
        Long liftedSanctionId = liftTemporarySuspensions(report, adminId, now);
        ReportStatus before = report.getStatus();
        report.dismiss(adminId, note, now);
        outboxEventRecorder.record(
                MemberReportEvents.RESOLVED_EVENT_TYPE,
                MemberReportEvents.AGGREGATE_TYPE,
                reportId,
                new MemberReportEvents.ResolvedPayload(
                        reportId, report.getReporterId(), ReportStatus.DISMISSED.name()));
        adminAuditRecorder.record(
                adminId,
                AdminAuditAction.REPORT_DISMISS,
                AdminAuditTargetType.MEMBER_REPORT,
                reportId,
                new TrustAuditDetails.Dismiss(before.name(), report.getStatus().name(), liftedSanctionId));
        log.info("member report dismissed reportId={} adminId={}", reportId, adminId);
        return new MemberReportStatusResult(reportId, report.getStatus().name());
    }

    // 임시 정지가 없으면(긴급 신고가 아니거나 이미 자연 만료됐으면) 회원의 정지 상태를 다시 계산할 이유가 없다.
    private Long liftTemporarySuspensions(MemberReport report, long adminId, Instant now) {
        List<Sanction> temporaries = sanctionRepository.findActiveTemporaryByReportId(report.getId());
        if (temporaries.isEmpty()) {
            return null;
        }
        temporaries.forEach(sanction -> sanction.lift(adminId, now));
        suspensionResyncer.resync(report.getTargetMemberId());
        return temporaries.get(0).getId();
    }
}
