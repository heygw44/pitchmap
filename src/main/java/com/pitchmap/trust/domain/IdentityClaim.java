package com.pitchmap.trust.domain;

/**
 * 회원이 본인확인을 요청하며 낸 값이다. 가짜 제공자는 이 값을 그대로 믿는다.
 *
 * @param birthYear 출생연도
 * @param gender 성별
 * @param demoIdentityKey 시연용 식별 문자열. 같은 값이면 같은 사람으로 본다.
 */
public record IdentityClaim(int birthYear, Gender gender, String demoIdentityKey) {

    /** 출생연도와 시연용 식별 문자열이 로그에 남지 않게 가린다. */
    @Override
    public String toString() {
        return "IdentityClaim[gender=" + gender + ", birthYear=****, demoIdentityKey=****]";
    }
}
