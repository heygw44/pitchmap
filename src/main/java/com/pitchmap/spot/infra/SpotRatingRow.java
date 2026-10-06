package com.pitchmap.spot.infra;

/** 장소 한 곳의 후기 수와 평균 평점. 후기가 없으면 count는 0이고 average는 {@code null}이다. average는 소수 첫째 자리까지 반올림한 값이다. */
public record SpotRatingRow(long count, Double average) {}
