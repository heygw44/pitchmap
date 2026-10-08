package com.pitchmap.spot.application;

import java.time.Instant;
import java.util.List;

/**
 * 관리자 박지 검토 목록의 한 항목. type과 status는 열거형 이름이다.
 *
 * <p>reporter는 박지를 제보한 회원이고, 공공데이터 장소이면 {@code null}이다. reportCount, reasonCounts, recentReports는 검토 전 신고만
 * 담는다. 신고자는 담지 않는다. statusChangedAt은 장소가 마지막으로 바뀐 시각이다.
 */
public record AdminSpotSummary(
        long spotId,
        String type,
        String name,
        String status,
        double lat,
        double lng,
        boolean parkWarning,
        Reporter reporter,
        long reportCount,
        ReasonCounts reasonCounts,
        List<RecentReport> recentReports,
        Instant statusChangedAt) {

    public record Reporter(long memberId, String nickname) {}

    public record ReasonCounts(long illegalArea, long closed, long falseInfo) {}

    public record RecentReport(String reason, String content, Instant createdAt) {}
}
