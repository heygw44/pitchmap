package com.pitchmap.weather.application;

import com.pitchmap.weather.infra.KasiApiException;
import com.pitchmap.weather.infra.KasiRiseSetItem;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * 천문연 원천 값을 일출·일몰 응답 값으로 바꾼다. 값을 해석하지 못하면 {@link KasiApiException}을 던져서, 호출한 쪽이 API 실패와 같은 방법으로
 * 처리하게 한다.
 */
final class KasiSunTimesConverter {

    private static final DateTimeFormatter LOCDATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    // 원천은 시각을 HHmm 네 자리 숫자로 주고, 해당 날짜에 값이 없으면 ----를 준다.
    private static final Pattern HHMM = Pattern.compile("\\d{4}");

    private KasiSunTimesConverter() {}

    static SunTimes toSunTimes(KasiRiseSetItem item) {
        return new SunTimes(
                parseDate(item.locdate()),
                parseTime(item.sunrise(), "sunrise"),
                parseTime(item.sunset(), "sunset"),
                parseTime(item.civile(), "civile"));
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value == null ? "" : value.strip(), LOCDATE);
        } catch (DateTimeParseException e) {
            throw new KasiApiException("천문연 응답의 locdate 값이 날짜가 아니다", e);
        }
    }

    private static LocalTime parseTime(String value, String field) {
        String text = value == null ? "" : value.strip();
        if (!HHMM.matcher(text).matches()) {
            throw new KasiApiException("천문연 응답의 " + field + " 값이 시각이 아니다");
        }
        try {
            return LocalTime.of(Integer.parseInt(text.substring(0, 2)), Integer.parseInt(text.substring(2)));
        } catch (DateTimeException e) {
            throw new KasiApiException("천문연 응답의 " + field + " 값이 시각 범위를 벗어났다", e);
        }
    }
}
