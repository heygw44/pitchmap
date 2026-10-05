package com.pitchmap.spot.application;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.spot.domain.SpotType;
import com.pitchmap.spot.infra.SpotDetailMapper;
import com.pitchmap.spot.infra.SpotDetailRow;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 장소 한 곳의 상세를 찾는다. 박지는 박지 상세를, 야영장과 자연휴양림은 공공데이터 상세를 채운다.
 *
 * <p>공공데이터 장소의 휴장 여부는 조회한 날의 한국 날짜로 계산한다.
 */
@Service
@RequiredArgsConstructor
public class SpotDetailQueryService {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private static final TypeReference<Map<String, String>> FACILITIES_TYPE = new TypeReference<>() {};

    private final SpotDetailMapper spotDetailMapper;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    /**
     * 호출하면 spotId인 장소의 상세를 돌려준다.
     *
     * <p>장소가 없거나 숨김, 삭제, 검토 대기처럼 ACTIVE가 아니면 서비스는 NOT_FOUND로 거부한다. 숨긴 장소가 있는지조차 드러내지 않으려고 두 경우를
     * 구분하지 않는다.
     */
    @Transactional(readOnly = true)
    public SpotDetail findDetail(long spotId) {
        SpotDetailRow row = spotDetailMapper
                .selectActiveDetail(spotId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        LocalDate today = LocalDate.now(clock.withZone(KOREA));
        return new SpotDetail(
                row.spotId(),
                row.type(),
                row.name(),
                row.lat(),
                row.lng(),
                row.address(),
                row.parkWarning(),
                toBakjiDetail(row),
                toPublicDetail(row, today));
    }

    // 박지 상세 행이 없는 박지는 데이터가 어긋난 경우라서, 서비스는 상세 없이 장소 정보만 돌려준다.
    private static SpotBakjiDetail toBakjiDetail(SpotDetailRow row) {
        if (row.type() != SpotType.BAKJI || row.reporterId() == null) {
            return null;
        }
        return new SpotBakjiDetail(
                row.description(),
                row.hasWater(),
                row.hasToilet(),
                row.signalLevel(),
                row.confirmationCount(),
                row.reporterId(),
                row.reporterNickname());
    }

    // 공공데이터 상세 행이 없는 야영장이나 휴양림도 데이터가 어긋난 경우라서, 서비스는 상세 없이 장소 정보만 돌려준다.
    private SpotPublicDetail toPublicDetail(SpotDetailRow row, LocalDate today) {
        if (row.type() == SpotType.BAKJI || row.source() == null) {
            return null;
        }
        boolean closedNow =
                SpotClosedNow.isClosedOn(row.type(), row.operatingStatus(), row.closedFrom(), row.closedUntil(), today);
        return new SpotPublicDetail(
                PublicSpotSource.valueOf(row.source()),
                row.category(),
                toFacilities(row.facilities()),
                row.phone(),
                row.homepage(),
                row.sourceDate(),
                toOperatingStatus(row.operatingStatus()),
                row.closedFrom(),
                row.closedUntil(),
                closedNow);
    }

    // 동기화는 고캠핑 원천 항목 이름을 키로 시설 정보를 저장한다. 서비스는 그 키를 응답 필드 이름으로 옮기고, 없는 키는 null로 둔다.
    private SpotFacilities toFacilities(String facilitiesJson) {
        if (facilitiesJson == null) {
            return null;
        }
        Map<String, String> source = jsonMapper.readValue(facilitiesJson, FACILITIES_TYPE);
        if (source == null || source.isEmpty()) {
            return null;
        }
        return new SpotFacilities(
                source.get("toiletCo"),
                source.get("swrmCo"),
                source.get("wtrplCo"),
                source.get("brazierCl"),
                source.get("sbrsCl"),
                source.get("sbrsEtc"),
                source.get("posblFcltyCl"),
                source.get("posblFcltyEtc"),
                source.get("animalCmgCl"));
    }

    private static PublicSpotOperatingStatus toOperatingStatus(String operatingStatus) {
        if (operatingStatus == null) {
            return null;
        }
        return PublicSpotOperatingStatus.valueOf(operatingStatus);
    }
}
