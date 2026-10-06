package com.pitchmap.weather.infra;

/**
 * 단기예보 응답의 항목 하나. 원천 값을 문자열 그대로 담는다.
 *
 * @param category 예보 항목 코드. 예: {@code TMP}(기온), {@code POP}(강수확률), {@code WSD}(풍속)
 * @param fcstDate 예보 날짜 {@code yyyyMMdd}(한국 시각)
 * @param fcstTime 예보 시각 {@code HHmm}(한국 시각)
 * @param fcstValue 예보 값
 */
public record KmaShortTermItem(String category, String fcstDate, String fcstTime, String fcstValue) {}
