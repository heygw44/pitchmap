package com.pitchmap.trust.domain;

import java.util.Optional;

/** 본인확인한 출생연도로 계산한 연령대다. 성인만 연령대를 가지며 20대부터 60대 이상까지 나눈다. */
public enum AgeGroup {
    TWENTIES,
    THIRTIES,
    FORTIES,
    FIFTIES,
    SIXTIES_PLUS;

    /**
     * 연 나이(올해 − 출생연도)로 연령대를 고른다. 19~29세는 20대, 60세 이상은 60대 이상이다.
     * 성인 기준(19세) 미만이면 연령대가 없으므로 빈 값을 돌려준다.
     */
    public static Optional<AgeGroup> fromAge(int age) {
        if (age < IdentityVerification.ADULT_AGE) {
            return Optional.empty();
        }
        if (age < 30) {
            return Optional.of(TWENTIES);
        }
        if (age < 40) {
            return Optional.of(THIRTIES);
        }
        if (age < 50) {
            return Optional.of(FORTIES);
        }
        if (age < 60) {
            return Optional.of(FIFTIES);
        }
        return Optional.of(SIXTIES_PLUS);
    }
}
