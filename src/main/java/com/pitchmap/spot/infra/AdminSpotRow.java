package com.pitchmap.spot.infra;

import java.time.Instant;

/**
 * 관리자 박지 검토 목록의 한 행. MyBatis가 이름으로 매핑하므로, 구성요소 이름은 열 별칭을 camelCase로 바꾼 것과 같아야 한다.
 *
 * <p>reporterId와 reporterNickname은 박지의 제보자다. 공공데이터 장소는 bakji_detail 행이 없어서 둘 다 {@code null}이다.
 * 신고 수 네 개는 검토 전 신고만 센 값이다.
 */
public record AdminSpotRow(
        long spotId,
        String type,
        String name,
        String status,
        double lat,
        double lng,
        boolean parkWarning,
        Long reporterId,
        String reporterNickname,
        long reportCount,
        long illegalAreaCount,
        long closedCount,
        long falseInfoCount,
        Instant statusChangedAt) {}
