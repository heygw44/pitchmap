package com.pitchmap.weather.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 장소 하나의 날씨 예보. 기상청 값이다.
 *
 * @param source 데이터 출처 표시
 * @param shortTerm 지금 이후의 시간별 단기예보(약 3일). 시각 오름차순
 * @param midTerm 오늘 이후의 중기예보(발표일 기준 4~10일 뒤). 날짜 오름차순
 */
public record WeatherForecast(String source, List<ShortTermForecast> shortTerm, List<MidTermForecast> midTerm) {

    public static final String KMA_SOURCE = "기상청";

    public WeatherForecast {
        shortTerm = List.copyOf(shortTerm);
        midTerm = List.copyOf(midTerm);
    }

    /**
     * 시간별 예보 하나. 원천에 없는 값은 {@code null}이다.
     *
     * @param at 예보 시각
     * @param temperature 기온(℃)
     * @param precipitationProbability 강수확률(%)
     * @param windSpeed 풍속(m/s)
     */
    public record ShortTermForecast(
            Instant at, Integer temperature, Integer precipitationProbability, Double windSpeed) {}

    /**
     * 날짜별 중기예보 하나. 원천에 없는 값은 {@code null}이다. 원천은 8~10일차를 오전·오후로 나누지 않아서, 이 날짜의 오전·오후에 같은 값을 넣는다.
     *
     * @param date 예보 날짜(한국 날짜)
     * @param minTemperature 최저 기온(℃)
     * @param maxTemperature 최고 기온(℃)
     * @param amSky 오전 하늘 상태(원천 문자열 그대로)
     * @param pmSky 오후 하늘 상태(원천 문자열 그대로)
     * @param amPrecipitationProbability 오전 강수확률(%)
     * @param pmPrecipitationProbability 오후 강수확률(%)
     */
    public record MidTermForecast(
            LocalDate date,
            Integer minTemperature,
            Integer maxTemperature,
            String amSky,
            String pmSky,
            Integer amPrecipitationProbability,
            Integer pmPrecipitationProbability) {}
}
