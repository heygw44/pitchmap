package com.pitchmap.spot.infra;

import java.time.LocalDate;

/** 모집 중인 베이스캠프 하나. 합류 조건 열은 DB 값 그대로여서 조건이 없으면 null이다. */
public record SpotRecruitingBasecampRow(
        long basecampId,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        int capacity,
        int headcount,
        Integer minTrustLevel,
        Integer ageGroupMin,
        Integer ageGroupMax,
        boolean sameGenderOnly) {}
