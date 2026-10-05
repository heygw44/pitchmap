package com.pitchmap.spot.infra;

import com.pitchmap.spot.domain.SpotType;
import java.time.LocalDate;

/**
 * 장소 상세 조회가 읽은 장소 한 건. MyBatis가 이름으로 매핑하므로, 구성요소 이름은 열 별칭을 camelCase로 바꾼 것과 같아야 한다.
 *
 * <p>description부터 reporterNickname까지는 bakji_detail과 제보한 회원의 값이고, 박지가 아니면 모두 {@code null}이다. confirmationCount는 박지
 * 확인 수이고, 박지가 아니면 0이다.
 *
 * <p>source부터 closedUntil까지는 public_spot_detail의 값이고, 공공데이터 상세가 없으면 모두 {@code null}이다. source와 operatingStatus는 enum
 * 이름이고, facilities는 MySQL이 정규화한 JSON 문자열이다.
 */
public record SpotDetailRow(
        long spotId,
        SpotType type,
        String name,
        double lat,
        double lng,
        String address,
        boolean parkWarning,
        String description,
        Boolean hasWater,
        Boolean hasToilet,
        String signalLevel,
        long confirmationCount,
        Long reporterId,
        String reporterNickname,
        String source,
        String category,
        String facilities,
        String phone,
        String homepage,
        LocalDate sourceDate,
        String operatingStatus,
        LocalDate closedFrom,
        LocalDate closedUntil) {}
