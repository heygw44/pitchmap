package com.pitchmap.spot.application;

/** 베이스캠프 합류 조건. 값이 null인 항목은 조건을 걸지 않았다는 뜻이다. */
public record SpotJoinCondition(
        Integer minTrustLevel, Integer ageGroupMin, Integer ageGroupMax, boolean sameGenderOnly) {}
