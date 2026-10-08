package com.pitchmap.basecamp.domain;

import java.time.LocalDate;

/** 베이스캠프를 열 때 캠프 리더가 정하는 내용이다. 출발일과 종료일은 한국 날짜다. */
public record BasecampDetails(
        String title,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        Capacity capacity,
        JoinCondition joinCondition) {}
