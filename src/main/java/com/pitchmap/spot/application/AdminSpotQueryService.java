package com.pitchmap.spot.application;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.spot.domain.SpotStatus;
import com.pitchmap.spot.infra.AdminSpotMapper;
import com.pitchmap.spot.infra.AdminSpotRecentReportRow;
import com.pitchmap.spot.infra.AdminSpotRow;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자가 검토 대기 또는 숨긴 장소의 목록을 읽는다. 신고 내용이 들어 있으므로 호출하는 쪽이 관리자 권한을 확인해야 한다. */
@Service
@RequiredArgsConstructor
public class AdminSpotQueryService {

    private final AdminSpotMapper adminSpotMapper;

    /**
     * 호출하면 status인 장소를 상태가 바뀐 시각이 이른 순서로 한 페이지 돌려준다. status가 null이면 검토 대기(PENDING_REVIEW)를 읽는다.
     * 검토 대기와 숨김(HIDDEN) 말고 다른 값이면 INVALID_INPUT으로 거부한다. 각 항목에는 검토 전 신고의 수, 사유별 수, 최근 5건을 담는다.
     */
    @Transactional(readOnly = true)
    public AdminSpotPage list(String status, int page, int size) {
        SpotStatus spotStatus = parseStatus(status);
        long offset = (long) page * size;
        List<AdminSpotRow> rows = adminSpotMapper.selectPage(spotStatus.name(), offset, size + 1);
        boolean hasNext = rows.size() > size;
        List<AdminSpotRow> pageRows = rows.stream().limit(size).toList();
        Map<Long, List<AdminSpotSummary.RecentReport>> recentBySpot = readRecentReports(pageRows);
        List<AdminSpotSummary> content = pageRows.stream()
                .map(row -> toSummary(row, recentBySpot.getOrDefault(row.spotId(), List.of())))
                .toList();
        return new AdminSpotPage(content, page, size, hasNext);
    }

    private Map<Long, List<AdminSpotSummary.RecentReport>> readRecentReports(List<AdminSpotRow> pageRows) {
        if (pageRows.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> spotIds = pageRows.stream().map(AdminSpotRow::spotId).toList();
        return adminSpotMapper.selectRecentReports(spotIds).stream()
                .collect(Collectors.groupingBy(
                        AdminSpotRecentReportRow::spotId,
                        Collectors.mapping(
                                row -> new AdminSpotSummary.RecentReport(row.reason(), row.content(), row.createdAt()),
                                Collectors.toList())));
    }

    private static SpotStatus parseStatus(String status) {
        if (status == null) {
            return SpotStatus.PENDING_REVIEW;
        }
        for (SpotStatus candidate : List.of(SpotStatus.PENDING_REVIEW, SpotStatus.HIDDEN)) {
            if (candidate.name().equals(status)) {
                return candidate;
            }
        }
        throw new BusinessException(CommonErrorCode.INVALID_INPUT, "status는 PENDING_REVIEW 또는 HIDDEN이어야 합니다.");
    }

    private static AdminSpotSummary toSummary(AdminSpotRow row, List<AdminSpotSummary.RecentReport> recentReports) {
        AdminSpotSummary.Reporter reporter = row.reporterId() == null
                ? null
                : new AdminSpotSummary.Reporter(row.reporterId(), row.reporterNickname());
        return new AdminSpotSummary(
                row.spotId(),
                row.type(),
                row.name(),
                row.status(),
                row.lat(),
                row.lng(),
                row.parkWarning(),
                reporter,
                row.reportCount(),
                new AdminSpotSummary.ReasonCounts(row.illegalAreaCount(), row.closedCount(), row.falseInfoCount()),
                recentReports,
                row.statusChangedAt());
    }
}
