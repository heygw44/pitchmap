package com.pitchmap.weather.application;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 한 위치의 하루 일출·일몰·시민박명 시각. 시각은 모두 한국 시각이다.
 *
 * @param date 날짜(한국 날짜)
 * @param sunrise 일출 시각
 * @param sunset 일몰 시각
 * @param civilTwilightEnd 저녁 시민박명이 끝나는 시각. 일몰 뒤 이 시각까지는 인공 조명 없이도 어느 정도 보인다.
 */
public record SunTimes(LocalDate date, LocalTime sunrise, LocalTime sunset, LocalTime civilTwilightEnd) {}
