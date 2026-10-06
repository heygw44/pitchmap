package com.pitchmap.spot.application;

import com.pitchmap.weather.application.SunTimes;
import com.pitchmap.weather.application.WeatherForecast;

/**
 * 장소 상세의 날씨. forecast는 기상청 예보이고, sun은 오늘(한국 날짜) 출몰시각이다.
 *
 * <p>천문연이 실패했으면 sun만 {@code null}이다. 기상청이 실패했으면 이 객체 자체를 만들지 않는다.
 */
public record SpotWeather(WeatherForecast forecast, SunTimes sun) {}
