package com.pitchmap.spot.application;

/** 장소의 평점. average는 후기 평점의 평균을 소수 첫째 자리에서 반올림한 값이고, 후기가 없으면 {@code null}이다. count는 후기 수다. */
public record SpotRating(Double average, long count) {}
