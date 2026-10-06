package com.pitchmap.spot.infra;

import com.pitchmap.spot.domain.SpotType;
import java.time.LocalDate;

/**
 * 장소 상세 조회가 읽은 장소 한 건. MyBatis가 이름으로 매핑하므로, 구성요소 이름은 열 별칭을 camelCase로 바꾼 것과 같아야 한다.
 *
 * <p>weatherNx와 weatherNy는 장소의 기상청 격자 좌표이고, 날씨 조회에 쓴다. areaName부터 areaSourceDate까지는 장소가 가리키는 공원 경계의 이름, 출처
 * 이름, 기준일이고, 경계를 가리키지 않으면 모두 {@code null}이다.
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
        int weatherNx,
        int weatherNy,
        boolean parkWarning,
        String areaName,
        String areaSource,
        LocalDate areaSourceDate,
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
