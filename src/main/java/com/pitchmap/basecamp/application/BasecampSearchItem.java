package com.pitchmap.basecamp.application;

import java.time.LocalDate;

/**
 * 검색 결과의 베이스캠프 한 건이다. headcount는 캠프 리더를 포함한 현재 인원이다.
 *
 * <p>eligibility는 로그인한 요청자에게만 채우고, 비로그인 요청자에게는 null이다.
 */
public record BasecampSearchItem(
        long basecampId,
        String title,
        SpotSummary spot,
        LocalDate startDate,
        LocalDate endDate,
        int capacity,
        int headcount,
        String status,
        JoinConditionSummary joinCondition,
        JoinEligibility eligibility) {

    /** 장소 요약이다. type은 장소 유형 이름이다. */
    public record SpotSummary(long spotId, String name, String type, double lat, double lng) {}
}
