package com.pitchmap.spot.application;

/** 제보한 박지와 가까운 기존 박지다. distanceM은 두 박지 사이의 구면 거리(m)를 정수로 반올림한 값이다. */
public record BakjiDuplicateCandidate(long spotId, String name, int distanceM) {}
