package com.pitchmap.basecamp.application;

import java.time.LocalDate;

/**
 * 베이스캠프를 열려는 요청 내용이다. 날짜는 한국 날짜이고, 합류 조건은 걸지 않으면 null이다.
 */
public record BasecampOpenCommand(
        long spotId,
        String title,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        int capacity,
        JoinConditionCommand joinCondition) {

    /** 합류 조건 요청이다. 값이 null인 조건은 걸지 않는다. 연령대는 하한과 상한을 함께 보내야 한다. */
    public record JoinConditionCommand(
            Integer minTrustLevel, Integer ageGroupMin, Integer ageGroupMax, boolean sameGenderOnly) {}
}
