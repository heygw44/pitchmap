package com.pitchmap.weather.infra;

/**
 * 출몰시각 응답의 항목 하나. 원천 값을 문자열 그대로 담되, 값 뒤에 붙는 공백은 뺀다.
 *
 * @param locdate 날짜 {@code yyyyMMdd}(한국 날짜)
 * @param sunrise 일출 시각 {@code HHmm}(한국 시각). 해당 날짜에 없으면 {@code ----}
 * @param sunset 일몰 시각 {@code HHmm}(한국 시각). 해당 날짜에 없으면 {@code ----}
 * @param civile 저녁 시민박명이 끝나는 시각 {@code HHmm}(한국 시각). 해당 날짜에 없으면 {@code ----}
 */
public record KasiRiseSetItem(String locdate, String sunrise, String sunset, String civile) {}
