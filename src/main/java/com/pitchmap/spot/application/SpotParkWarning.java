package com.pitchmap.spot.application;

import java.time.LocalDate;

/**
 * 장소 상세와 박지 제보 결과의 공원 경계 경고. 경고가 아니면 warned만 false이고 나머지는 모두 {@code null}이다.
 *
 * <p>경고이면 notice와 guide를 채운다. areaName, source, sourceDate는 장소가 가리키는 공원 경계 행의 값이고, 경계 행이 없으면 {@code null}이다.
 * source는 경계 데이터의 출처 이름이다.
 */
public record SpotParkWarning(
        boolean warned, String areaName, String source, LocalDate sourceDate, String notice, String guide) {

    static SpotParkWarning notWarned() {
        return new SpotParkWarning(false, null, null, null, null, null);
    }

    static SpotParkWarning warned(String areaName, String source, LocalDate sourceDate) {
        return new SpotParkWarning(
                true, areaName, source, sourceDate, ParkWarningTexts.NOTICE, ParkWarningTexts.WARNED_GUIDE);
    }
}
