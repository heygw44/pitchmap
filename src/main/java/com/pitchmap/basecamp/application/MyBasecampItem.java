package com.pitchmap.basecamp.application;

import java.time.LocalDate;

/**
 * 내 베이스캠프 목록의 한 건이다. headcount는 캠프 리더를 포함한 현재 인원이고, myRelation은 LEADER, MEMBER, APPLICANT 중 하나다.
 */
public record MyBasecampItem(
        long basecampId,
        String title,
        SpotSummary spot,
        LocalDate startDate,
        LocalDate endDate,
        int capacity,
        int headcount,
        String status,
        String myRelation) {

    /** 장소 요약이다. type은 장소 유형 이름이다. */
    public record SpotSummary(long spotId, String name, String type) {}
}
