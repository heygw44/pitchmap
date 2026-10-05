package com.pitchmap.spot.infra;

import com.pitchmap.spot.domain.SpotType;
import java.time.LocalDate;

/**
 * 지도 영역 조회가 읽은 장소 한 건. MyBatis가 이름으로 매핑하므로, 구성요소 이름은 열 별칭을 camelCase로 바꾼 것과 같아야 한다.
 *
 * <p>operatingStatus, closedFrom, closedUntil은 public_spot_detail의 운영 상태 이름과 휴장 기간이다. 박지처럼 공공데이터 상세가 없는 장소는 셋 다
 * {@code null}이다.
 */
public record SpotMarkerRow(
        long spotId,
        SpotType type,
        String name,
        double lat,
        double lng,
        boolean parkWarning,
        String operatingStatus,
        LocalDate closedFrom,
        LocalDate closedUntil) {}
