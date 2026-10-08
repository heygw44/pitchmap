package com.pitchmap.spot.application;

import java.time.LocalDate;

/** 장소 상세에 보여 주는 모집 중인 베이스캠프. headcount는 캠프 리더를 포함한 현재 인원이다. */
public record SpotRecruitingBasecamp(
        long basecampId,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        int capacity,
        int headcount,
        SpotJoinCondition joinCondition) {}
