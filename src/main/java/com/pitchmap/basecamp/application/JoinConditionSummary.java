package com.pitchmap.basecamp.application;

/** 응답에 싣는 합류 조건이다. 값이 null인 조건은 걸지 않은 것이다. 연령대는 20~60의 십 단위이고 60은 60대 이상이다. */
public record JoinConditionSummary(
        Integer minTrustLevel, Integer ageGroupMin, Integer ageGroupMax, boolean sameGenderOnly) {}
