package com.pitchmap.basecamp.infra;

import com.pitchmap.basecamp.domain.BasecampStatus;
import com.pitchmap.basecamp.domain.JoinGender;
import java.time.LocalDate;

/**
 * 검색이 읽은 베이스캠프 한 건이다. MyBatis가 이름으로 매핑하므로, 구성요소 이름은 열 별칭을 camelCase로 바꾼 것과 같아야 한다.
 *
 * <p>spotType은 장소 모듈의 유형 이름 문자열이다. headcount는 상태가 ACTIVE인 멤버 수이고, 합류 조건 값은 걸지 않은 조건이면 null(sameGenderOnly는
 * false)이다. requiredGender는 동성만 받을 때 캠프 리더의 본인확인 성별이다.
 */
public record BasecampSearchRow(
        long basecampId,
        String title,
        long spotId,
        String spotName,
        String spotType,
        double spotLat,
        double spotLng,
        LocalDate startDate,
        LocalDate endDate,
        int capacity,
        int headcount,
        BasecampStatus status,
        Integer minTrustLevel,
        Integer ageGroupMin,
        Integer ageGroupMax,
        boolean sameGenderOnly,
        JoinGender requiredGender) {}
