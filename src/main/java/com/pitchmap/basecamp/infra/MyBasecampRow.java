package com.pitchmap.basecamp.infra;

import com.pitchmap.basecamp.domain.BasecampRelation;
import com.pitchmap.basecamp.domain.BasecampStatus;
import java.time.LocalDate;

/**
 * 내 베이스캠프 목록이 읽은 한 건이다. MyBatis가 이름으로 매핑하므로, 구성요소 이름은 열 별칭을 camelCase로 바꾼 것과 같아야 한다.
 *
 * <p>myRelation은 LEADER, MEMBER, APPLICANT 중 하나다. spotType은 장소 모듈의 유형 이름 문자열이고, headcount는 상태가 ACTIVE인 멤버 수다.
 */
public record MyBasecampRow(
        long basecampId,
        String title,
        long spotId,
        String spotName,
        String spotType,
        LocalDate startDate,
        LocalDate endDate,
        int capacity,
        int headcount,
        BasecampStatus status,
        BasecampRelation myRelation) {}
