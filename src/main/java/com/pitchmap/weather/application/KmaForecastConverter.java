package com.pitchmap.weather.application;

import com.pitchmap.weather.application.WeatherForecast.MidTermForecast;
import com.pitchmap.weather.application.WeatherForecast.ShortTermForecast;
import com.pitchmap.weather.infra.KmaApiException;
import com.pitchmap.weather.infra.KmaMidTermItem;
import com.pitchmap.weather.infra.KmaShortTermItem;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 기상청 원천 값을 날씨 응답 값으로 바꾼다. 값을 해석하지 못하면 {@link KmaApiException}을 던져서, 호출한 쪽이 API 실패와 같은 방법으로 처리하게 한다.
 */
final class KmaForecastConverter {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter FCST_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final String TEMPERATURE = "TMP";
    private static final String PRECIPITATION_PROBABILITY = "POP";
    private static final String WIND_SPEED = "WSD";
    private static final int FIRST_MID_TERM_DAY = 4;
    private static final int LAST_MID_TERM_DAY = 10;
    // 발표일 기준 7일차까지는 오전·오후로 나눠 주고, 8일차부터는 하루 값 하나만 준다.
    private static final int LAST_HALF_DAY = 7;

    private KmaForecastConverter() {}

    /** 같은 예보 시각의 기온·강수확률·풍속을 한 항목으로 묶는다. {@code now}보다 앞선 시각은 뺀다. */
    static List<ShortTermForecast> toShortTerm(List<KmaShortTermItem> items, Instant now) {
        TreeMap<Instant, ShortTermBuilder> byTime = new TreeMap<>();
        for (KmaShortTermItem item : items) {
            if (!isWanted(item.category())) {
                continue;
            }
            Instant at = parseInstant(item);
            if (at.isBefore(now)) {
                continue;
            }
            byTime.computeIfAbsent(at, ShortTermBuilder::new).put(item);
        }
        return byTime.values().stream().map(ShortTermBuilder::build).toList();
    }

    /** 육상예보와 기온예보를 날짜별로 묶는다. {@code tmFc}의 4~10일 뒤 가운데 {@code today} 이후 날짜만 남기고, 값이 하나도 없는 날짜는 뺀다. */
    static List<MidTermForecast> toMidTerm(
            Optional<KmaMidTermItem> land, Optional<KmaMidTermItem> ta, LocalDate tmFcDate, LocalDate today) {
        List<MidTermForecast> result = new ArrayList<>();
        for (int day = FIRST_MID_TERM_DAY; day <= LAST_MID_TERM_DAY; day++) {
            LocalDate date = tmFcDate.plusDays(day);
            if (date.isBefore(today)) {
                continue;
            }
            MidTermForecast forecast = toMidTermDay(land.orElse(null), ta.orElse(null), day, date);
            if (hasAnyValue(forecast)) {
                result.add(forecast);
            }
        }
        return result;
    }

    private static MidTermForecast toMidTermDay(KmaMidTermItem land, KmaMidTermItem ta, int day, LocalDate date) {
        String am = day <= LAST_HALF_DAY ? "Am" : "";
        String pm = day <= LAST_HALF_DAY ? "Pm" : "";
        return new MidTermForecast(
                date,
                integerOf(ta, "taMin" + day),
                integerOf(ta, "taMax" + day),
                textOf(land, "wf" + day + am),
                textOf(land, "wf" + day + pm),
                integerOf(land, "rnSt" + day + am),
                integerOf(land, "rnSt" + day + pm));
    }

    private static boolean hasAnyValue(MidTermForecast f) {
        return f.minTemperature() != null
                || f.maxTemperature() != null
                || f.amSky() != null
                || f.pmSky() != null
                || f.amPrecipitationProbability() != null
                || f.pmPrecipitationProbability() != null;
    }

    private static boolean isWanted(String category) {
        return TEMPERATURE.equals(category)
                || PRECIPITATION_PROBABILITY.equals(category)
                || WIND_SPEED.equals(category);
    }

    private static Instant parseInstant(KmaShortTermItem item) {
        try {
            return LocalDateTime.parse(item.fcstDate() + item.fcstTime(), FCST_DATE_TIME)
                    .atZone(KST)
                    .toInstant();
        } catch (DateTimeParseException e) {
            throw new KmaApiException("기상청 단기예보의 예보 시각을 해석하지 못했다", e);
        }
    }

    private static String textOf(KmaMidTermItem item, String field) {
        return item == null ? null : item.get(field);
    }

    private static Integer integerOf(KmaMidTermItem item, String field) {
        String value = textOf(item, field);
        return value == null ? null : parseInteger(value, field);
    }

    private static Integer parseInteger(String value, String field) {
        try {
            return Integer.valueOf(value.strip());
        } catch (NumberFormatException e) {
            throw new KmaApiException("기상청 응답의 " + field + " 값이 정수가 아니다", e);
        }
    }

    private static final class ShortTermBuilder {

        private final Instant at;
        private Integer temperature;
        private Integer precipitationProbability;
        private Double windSpeed;

        ShortTermBuilder(Instant at) {
            this.at = at;
        }

        void put(KmaShortTermItem item) {
            String value = item.fcstValue();
            switch (item.category()) {
                case TEMPERATURE -> temperature = parseInteger(value, TEMPERATURE);
                case PRECIPITATION_PROBABILITY ->
                    precipitationProbability = parseInteger(value, PRECIPITATION_PROBABILITY);
                default -> windSpeed = parseDouble(value);
            }
        }

        ShortTermForecast build() {
            return new ShortTermForecast(at, temperature, precipitationProbability, windSpeed);
        }

        private static Double parseDouble(String value) {
            try {
                return Double.valueOf(value.strip());
            } catch (NumberFormatException | NullPointerException e) {
                throw new KmaApiException("기상청 응답의 " + WIND_SPEED + " 값이 숫자가 아니다", e);
            }
        }
    }
}
