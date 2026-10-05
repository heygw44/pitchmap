package com.pitchmap.spot.application;

import com.pitchmap.spot.domain.GeoPoint;
import com.pitchmap.spot.domain.WeatherGrid;
import com.pitchmap.spot.infra.PublicSpotColumns;
import com.pitchmap.spot.infra.PublicSpotRow;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * 저장할 수 있게 다듬은 공공데이터 장소 한 건.
 *
 * <p>원천 데이터에는 앞뒤 공백, 빈 문자열, 열 크기보다 긴 값이 섞여 온다. 그래서 {@link #from}을 호출하면 문자열의 앞뒤 공백을 지우고, 빈 문자열은
 * null로 바꾸고, 열 크기를 넘는 값은 열 크기에 맞춰 자른다. 다만 홈페이지 주소는 자르면 열리지 않는 주소가 되므로 자르지 않고 버린다.
 *
 * <p>운영 상태와 휴장 기간은 원천이 준 대로 둔다. 휴장 시작일이 종료일보다 늦어도 거부하지 않는다.
 */
record NormalizedPublicSpot(
        String externalId,
        String name,
        GeoPoint location,
        WeatherGrid weatherGrid,
        String address,
        String category,
        SortedMap<String, String> facilities,
        String phone,
        String homepage,
        PublicSpotOperatingStatus operatingStatus,
        LocalDate closedFrom,
        LocalDate closedUntil) {

    // 열 크기는 spot, public_spot_detail 테이블 정의와 같다. MySQL VARCHAR는 바이트가 아니라 문자 수로 길이를 센다.
    static final int MAX_EXTERNAL_ID_LENGTH = 50;
    static final int MAX_NAME_LENGTH = 100;
    static final int MAX_ADDRESS_LENGTH = 255;
    static final int MAX_CATEGORY_LENGTH = 50;
    static final int MAX_PHONE_LENGTH = 30;
    static final int MAX_HOMEPAGE_LENGTH = 255;

    // MySQL은 SRID 4326 점의 경도를 읽을 때 부동소수 오차를 낸다. 예를 들어 127.7298을 저장하면 ST_Longitude가 127.72979999999998을 돌려준다.
    // 그래서 좌표를 정확히 같은지로 비교하면 원천 값이 그대로여도 동기화할 때마다 바뀐 장소로 보고 갱신한다.
    // 서버는 차이가 이 값(약 1cm) 이하이면 같은 좌표로 본다.
    static final double COORDINATE_TOLERANCE_DEGREES = 1e-7;

    /**
     * 호출하면 명령의 값을 다듬는다. 외부 ID나 이름이 비어 있거나, 외부 ID가 열 크기를 넘거나, 좌표가 기상청 격자 밖이면
     * {@link IllegalArgumentException}을 던진다. 외부 ID는 원천 행을 찾는 열쇠라서 잘라서 저장하면 다른 행과 겹칠 수 있으므로 자르지 않는다.
     */
    static NormalizedPublicSpot from(PublicSpotCommand command) {
        String externalId = requireText(command.externalId(), "외부 ID가 비어 있습니다.");
        if (lengthOf(externalId) > MAX_EXTERNAL_ID_LENGTH) {
            throw new IllegalArgumentException("외부 ID가 " + MAX_EXTERNAL_ID_LENGTH + "자를 넘습니다.");
        }
        String name = requireText(command.name(), "이름이 비어 있습니다.");
        GeoPoint location = new GeoPoint(command.latitude(), command.longitude());
        return new NormalizedPublicSpot(
                externalId,
                truncate(name, MAX_NAME_LENGTH),
                location,
                WeatherGrid.from(location),
                truncate(blankToNull(command.address()), MAX_ADDRESS_LENGTH),
                truncate(blankToNull(command.category()), MAX_CATEGORY_LENGTH),
                normalizeFacilities(command.facilities()),
                truncate(blankToNull(command.phone()), MAX_PHONE_LENGTH),
                dropIfTooLong(blankToNull(command.homepage()), MAX_HOMEPAGE_LENGTH),
                command.operatingStatus(),
                command.closedFrom(),
                command.closedUntil());
    }

    /** 호출하면 spot 테이블에 저장한 이름, 주소, 좌표 중 하나라도 이 값과 다른지 돌려준다. */
    boolean differsInSpotColumns(PublicSpotRow stored) {
        return !Objects.equals(name, stored.name())
                || !Objects.equals(address, stored.address())
                || !isSameDegree(location.latitude(), stored.latitude())
                || !isSameDegree(location.longitude(), stored.longitude());
    }

    /**
     * 호출하면 public_spot_detail에 저장한 분류, 시설, 전화번호, 홈페이지, 운영 상태, 휴장 기간 중 하나라도 이 값과 다른지 돌려준다. 값이 없는
     * 쪽(null)과 있는 쪽은 다른 값으로 본다.
     */
    boolean differsInDetailColumns(PublicSpotRow stored, Map<String, String> storedFacilities) {
        return !Objects.equals(category, stored.category())
                || !Objects.equals(facilities, storedFacilities)
                || !Objects.equals(phone, stored.phone())
                || !Objects.equals(homepage, stored.homepage())
                || !Objects.equals(operatingStatusName(), stored.operatingStatus())
                || !Objects.equals(closedFrom, stored.closedFrom())
                || !Objects.equals(closedUntil, stored.closedUntil());
    }

    PublicSpotColumns toColumns(String facilitiesJson) {
        return new PublicSpotColumns(
                externalId,
                name,
                location.latitude(),
                location.longitude(),
                address,
                weatherGrid.nx(),
                weatherGrid.ny(),
                category,
                facilitiesJson,
                phone,
                homepage,
                operatingStatusName(),
                closedFrom,
                closedUntil);
    }

    private String operatingStatusName() {
        if (operatingStatus == null) {
            return null;
        }
        return operatingStatus.name();
    }

    private static String requireText(String value, String message) {
        String text = blankToNull(value);
        if (text == null) {
            throw new IllegalArgumentException(message);
        }
        return text;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    // 키 순서를 고정해 두면 같은 시설 정보가 언제나 같은 JSON 문자열이 된다. 값이 빈 항목은 정보가 없는 것과 같으므로 뺀다.
    private static SortedMap<String, String> normalizeFacilities(Map<String, String> source) {
        if (source == null) {
            return null;
        }
        SortedMap<String, String> facilities = new TreeMap<>();
        source.forEach((key, value) -> {
            String item = blankToNull(value);
            if (key != null && !key.isBlank() && item != null) {
                facilities.put(key.strip(), item);
            }
        });
        if (facilities.isEmpty()) {
            return null;
        }
        return Collections.unmodifiableSortedMap(facilities);
    }

    // 이모지처럼 Java char 두 개로 표현하는 문자도 MySQL은 한 글자로 센다. 그래서 길이를 코드 포인트 수로 센다.
    private static String truncate(String value, int maxLength) {
        if (value == null || lengthOf(value) <= maxLength) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxLength)).strip();
    }

    private static String dropIfTooLong(String value, int maxLength) {
        if (value == null || lengthOf(value) <= maxLength) {
            return value;
        }
        return null;
    }

    private static int lengthOf(String value) {
        return value.codePointCount(0, value.length());
    }

    private static boolean isSameDegree(double source, double stored) {
        return Math.abs(source - stored) <= COORDINATE_TOLERANCE_DEGREES;
    }
}
