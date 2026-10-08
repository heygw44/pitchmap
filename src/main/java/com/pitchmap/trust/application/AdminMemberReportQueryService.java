package com.pitchmap.trust.application;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.trust.domain.ReportStatus;
import com.pitchmap.trust.infra.AdminMemberReportDetailRow;
import com.pitchmap.trust.infra.AdminMemberReportMapper;
import com.pitchmap.trust.infra.AdminMemberReportRow;
import com.pitchmap.trust.infra.AdminSanctionHistoryRow;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자가 신고 목록과 상세를 읽는다. 신고 내용이 들어 있으므로 호출하는 쪽이 관리자 권한을 확인해야 한다. */
@Service
@RequiredArgsConstructor
public class AdminMemberReportQueryService {

    private final AdminMemberReportMapper adminMemberReportMapper;

    /**
     * 호출하면 신고를 긴급 신고, 금전 요구 신고, 나머지 순으로, 같은 묶음 안에서는 접수 시각이 이른 순서로 한 페이지 돌려준다.
     * status나 urgent가 null이면 그 조건으로 거르지 않는다. status가 알 수 없는 상태 이름이면 INVALID_INPUT으로 거부한다.
     */
    @Transactional(readOnly = true)
    public AdminMemberReportPage list(String status, Boolean urgent, int page, int size) {
        String statusName = status == null ? null : parseStatus(status).name();
        long offset = (long) page * size;
        List<AdminMemberReportRow> rows = adminMemberReportMapper.selectList(statusName, urgent, offset, size + 1);
        boolean hasNext = rows.size() > size;
        List<AdminMemberReportSummary> content = rows.stream()
                .limit(size)
                .map(AdminMemberReportQueryService::toSummary)
                .toList();
        return new AdminMemberReportPage(content, page, size, hasNext);
    }

    /** 호출하면 신고 한 건의 상세를 돌려준다. 신고가 없으면 NOT_FOUND로 거부한다. */
    @Transactional(readOnly = true)
    public AdminMemberReportDetail detail(long reportId) {
        AdminMemberReportDetailRow row = adminMemberReportMapper
                .selectDetail(reportId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        List<AdminMemberReportDetail.SanctionHistory> history =
                adminMemberReportMapper.selectSanctionHistory(row.targetId()).stream()
                        .map(AdminMemberReportQueryService::toHistory)
                        .toList();
        return new AdminMemberReportDetail(
                toSummary(row),
                row.content(),
                row.resultNote(),
                row.handledBy(),
                row.handledAt(),
                new AdminMemberReportDetail.Basecamp(
                        row.basecampId(), row.basecampTitle(), row.basecampStatus(), row.basecampStartDate()),
                toReview(row),
                history);
    }

    private static ReportStatus parseStatus(String status) {
        try {
            return ReportStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "알 수 없는 신고 상태입니다.");
        }
    }

    private AdminMemberReportDetail.Review toReview(AdminMemberReportDetailRow row) {
        if (row.reviewId() == null) {
            return null;
        }
        return new AdminMemberReportDetail.Review(
                row.reviewComment(),
                adminMemberReportMapper.selectReviewTags(row.reviewId()),
                Boolean.TRUE.equals(row.reviewRejoinWanted()),
                Boolean.TRUE.equals(row.reviewHidden()));
    }

    private static AdminMemberReportSummary toSummary(AdminMemberReportRow row) {
        return new AdminMemberReportSummary(
                row.reportId(),
                row.kind(),
                row.type(),
                row.urgent(),
                row.status(),
                row.reporterId(),
                row.reporterNickname(),
                row.targetId(),
                row.targetNickname(),
                row.basecampId(),
                row.createdAt());
    }

    private static AdminMemberReportSummary toSummary(AdminMemberReportDetailRow row) {
        return new AdminMemberReportSummary(
                row.reportId(),
                row.kind(),
                row.type(),
                row.urgent(),
                row.status(),
                row.reporterId(),
                row.reporterNickname(),
                row.targetId(),
                row.targetNickname(),
                row.basecampId(),
                row.createdAt());
    }

    private static AdminMemberReportDetail.SanctionHistory toHistory(AdminSanctionHistoryRow row) {
        return new AdminMemberReportDetail.SanctionHistory(
                row.sanctionId(),
                row.type(),
                row.level(),
                row.status(),
                row.reason(),
                row.startsAt(),
                row.endsAt(),
                row.liftedAt());
    }
}
