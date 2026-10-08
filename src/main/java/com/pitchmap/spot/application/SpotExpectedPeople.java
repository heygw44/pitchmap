package com.pitchmap.spot.application;

import java.time.LocalDate;

/** 야영하는 밤 하나의 예상 인원. date 밤에 이 장소에서 야영할 사람이 count명이라는 뜻이다. */
public record SpotExpectedPeople(LocalDate date, int count) {}
