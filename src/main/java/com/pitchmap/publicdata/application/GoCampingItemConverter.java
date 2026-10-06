package com.pitchmap.publicdata.application;

import com.pitchmap.publicdata.infra.GoCampingItem;
import com.pitchmap.spot.application.PublicSpotCommand;
import com.pitchmap.spot.application.PublicSpotOperatingStatus;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

/**
 * 고캠핑 응답 항목을 장소 적재 명령으로 바꾼다.
 *
 * <p>고캠핑은 값이 없는 필드를 빈 문자열로 준다. 그래서 변환기는 공백뿐인 값을 null로 바꾸고, 나머지 값은 앞뒤 공백을 지운다.
 * 응답의 mapX는 경도, mapY는 위도다.
 */
@Slf4j
final class GoCampingItemConverter {

    // 원천 항목 이름을 그대로 facilities JSON의 키로 쓴다. 순서를 고정해 두면 같은 데이터는 늘 같은 JSON이 된다.
    private static final Map<String, Function<GoCampingItem, String>> FACILITY_FIELDS = facilityFields();

    private static final String REMOVED_SYNC_STATUS = "D";

    private static final Map<String, PublicSpotOperatingStatus> OPERATING_STATUSES = Map.of(
            "운영", PublicSpotOperatingStatus.OPERATING,
            "휴장", PublicSpotOperatingStatus.TEMPORARILY_CLOSED,
            "폐업", PublicSpotOperatingStatus.PERMANENTLY_CLOSED);

    private GoCampingItemConverter() {}

    /**
     * 호출하면 항목을 명령으로 바꾼다. ID·이름이 비었거나 좌표를 숫자로 읽을 수 없으면 WARN 로그를 남기고 빈 값을 돌려준다.
     * 운영 상태와 휴장 기간은 읽을 수 없으면 null로 두고 항목은 그대로 적재한다.
     */
    static Optional<PublicSpotCommand> convert(GoCampingItem item) {
        String externalId = blankToNull(item.contentId());
        String name = blankToNull(item.facltNm());
        if (externalId == null || name == null) {
            log.warn("gocamping item skipped: missing id or name contentId={}", externalId);
            return Optional.empty();
        }
        Double latitude = parseCoordinate(item.mapY());
        Double longitude = parseCoordinate(item.mapX());
        if (latitude == null || longitude == null) {
            log.warn("gocamping item skipped: unparseable coordinate contentId={}", externalId);
            return Optional.empty();
        }
        return Optional.of(new PublicSpotCommand(
                externalId,
                name,
                latitude,
                longitude,
                joinAddress(item.addr1(), item.addr2()),
                blankToNull(item.induty()),
                facilities(item),
                blankToNull(item.tel()),
                blankToNull(item.homepage()),
                operatingStatus(externalId, item.manageSttus()),
                closedDate(externalId, "hvofBgnde", item.hvofBgnde()),
                closedDate(externalId, "hvofEnddle", item.hvofEnddle()),
                null));
    }

    /** 호출하면 원천에서 삭제된 항목인지 알려 준다. 동기화 목록의 syncStatus가 정확히 D일 때만 삭제로 본다. 다른 값이나 빈 값은 모두 살아 있는 항목이다. */
    static boolean isRemoved(GoCampingItem item) {
        return REMOVED_SYNC_STATUS.equals(blankToNull(item.syncStatus()));
    }

    // 운영 상태는 원천의 값을 그대로 저장만 한다. 모르는 값은 상태를 알 수 없는 것으로 두고 원천에 새 값이 생겼음을 알리려고 WARN을 남긴다.
    private static PublicSpotOperatingStatus operatingStatus(String externalId, String value) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            return null;
        }
        PublicSpotOperatingStatus status = OPERATING_STATUSES.get(trimmed);
        if (status == null) {
            log.warn("gocamping item has unknown operating status contentId={} manageSttus={}", externalId, trimmed);
        }
        return status;
    }

    private static LocalDate closedDate(String externalId, String field, String value) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return LocalDate.parse(trimmed);
        } catch (DateTimeParseException e) {
            log.warn("gocamping item has unparseable closed date contentId={} field={}", externalId, field);
            return null;
        }
    }

    private static Double parseCoordinate(String value) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return Double.valueOf(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String joinAddress(String addr1, String addr2) {
        String base = blankToNull(addr1);
        String detail = blankToNull(addr2);
        if (detail == null) {
            return base;
        }
        if (base == null) {
            return detail;
        }
        return base + " " + detail;
    }

    private static Map<String, String> facilities(GoCampingItem item) {
        Map<String, String> facilities = new LinkedHashMap<>();
        FACILITY_FIELDS.forEach((key, getter) -> {
            String value = blankToNull(getter.apply(item));
            if (value != null) {
                facilities.put(key, value);
            }
        });
        return facilities;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private static Map<String, Function<GoCampingItem, String>> facilityFields() {
        Map<String, Function<GoCampingItem, String>> fields = new LinkedHashMap<>();
        fields.put("toiletCo", GoCampingItem::toiletCo);
        fields.put("swrmCo", GoCampingItem::swrmCo);
        fields.put("wtrplCo", GoCampingItem::wtrplCo);
        fields.put("brazierCl", GoCampingItem::brazierCl);
        fields.put("sbrsCl", GoCampingItem::sbrsCl);
        fields.put("sbrsEtc", GoCampingItem::sbrsEtc);
        fields.put("posblFcltyCl", GoCampingItem::posblFcltyCl);
        fields.put("posblFcltyEtc", GoCampingItem::posblFcltyEtc);
        fields.put("animalCmgCl", GoCampingItem::animalCmgCl);
        return Collections.unmodifiableMap(fields);
    }
}
