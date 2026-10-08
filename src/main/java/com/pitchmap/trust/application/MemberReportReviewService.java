package com.pitchmap.trust.application;

import com.pitchmap.common.audit.AdminAuditAction;
import com.pitchmap.common.audit.AdminAuditRecorder;
import com.pitchmap.common.audit.AdminAuditTargetType;
import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.trust.domain.MemberReport;
import com.pitchmap.trust.domain.MemberReportRepository;
import com.pitchmap.trust.domain.ReportStatus;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자가 접수된 신고의 검토를 시작한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberReportReviewService {

    private final MemberReportRepository memberReportRepository;
    private final AdminAuditRecorder adminAuditRecorder;
    private final Clock clock;

    /**
     * 호출하면 신고를 접수 상태에서 검토 중으로 바꾸고 감사 로그를 한 줄 남긴다. 신고 행을 쓰기 잠금으로 읽으므로 같은 신고를 동시에
     * 처리하면 한 줄로 세워진다. 신고가 없으면 NOT_FOUND, 접수 상태가 아니면 REPORT_INVALID_STATE로 거부한다.
     */
    @Transactional
    public MemberReportStatusResult startReview(long reportId, long adminId) {
        MemberReport report = memberReportRepository
                .findByIdForUpdate(reportId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        ReportStatus before = report.getStatus();
        report.startReview(adminId, clock.instant());
        adminAuditRecorder.record(
                adminId,
                AdminAuditAction.REPORT_START_REVIEW,
                AdminAuditTargetType.MEMBER_REPORT,
                reportId,
                new TrustAuditDetails.StartReview(
                        before.name(), report.getStatus().name()));
        log.info("member report review started reportId={} adminId={}", reportId, adminId);
        return new MemberReportStatusResult(reportId, report.getStatus().name());
    }
}
